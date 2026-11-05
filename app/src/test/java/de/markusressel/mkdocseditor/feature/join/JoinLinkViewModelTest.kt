package de.markusressel.mkdocseditor.feature.join

import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AccessRequestCreated
import de.markusressel.mkdocseditor.auth.api.AccessRequestState
import de.markusressel.mkdocseditor.auth.api.AccessRequestStatus
import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import de.markusressel.mkdocseditor.auth.discovery.DiscoveredServer
import de.markusressel.mkdocseditor.auth.discovery.DiscoveryResult
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AccessRequestStore
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.auth.token.PendingAccessRequest
import de.markusressel.mkdocseditor.auth.token.TokenCipher
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.join.data.JoinTarget
import de.markusressel.mkdocseditor.feature.join.data.PendingJoin
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.JoinEvent
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.Problem
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class JoinLinkViewModelTest {

    private val backendUrl = "https://wiki.example.com/"
    private val link = "https://edit.example.com/join/doc?from=sender"

    private class MapStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private val api = FakeAuthApi()
    private val tokens = InMemoryTokenStore()
    private val backends = FakeAuthBackends()
    private val requestStore = AccessRequestStore(MapStore(), object : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain
        override fun decrypt(data: ByteArray) = data
    })
    private val pendingJoin = PendingJoin(MapStore())
    private val navigation = MainNavigationRequests()
    private val events = mutableListOf<JoinEvent>()
    private var info = ServerInfo(name = "Wiki", features = listOf(ServerFeature.ACCESS_REQUESTS))

    private fun viewModel() = JoinLinkViewModel(
        discovery = {
            DiscoveryResult.Found(DiscoveredServer(backendUrl, info, wikiUrl = null, webClientUrl = "https://edit.example.com/"))
        },
        backends = backends,
        authRepository = AuthRepository(tokens, { api }, deviceName = "Pixel 8"),
        requestStore = requestStore,
        pendingJoin = pendingJoin,
        navigation = navigation,
    )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun signedInBackend() = backendConfig(1, domain = "wiki.example.com").also {
        backends.configs += it
        tokens.put(1, AuthSession("t", null, "anna"))
    }

    @Test
    fun `someone who is signed in goes right to the document`() = runTest {
        signedInBackend()
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.initialize(link)
        pendingJoin.release(1)

        assertEquals(JoinTarget(1, "doc", "sender"), pendingJoin.open.value)
        assertEquals(1L, backends.selectedId)
        assertEquals(listOf<JoinEvent>(JoinEvent.Close), events)
    }

    @Test
    fun `an unknown server is only added once the user chooses to sign in`() = runTest {
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

        viewModel.initialize(link)

        assertEquals(Step.Welcome, viewModel.uiState.value.step)
        assertEquals(emptyList(), backends.connected)

        viewModel.onUiEvent(UiEvent.SignInClicked)

        val id = (events.single() as JoinEvent.OpenLogin).backendId
        pendingJoin.release(id)
        assertEquals("doc", pendingJoin.open.value?.documentId)
    }

    @Test
    fun `a request stays stored and names the document and the sender`() = runTest {
        api.onCreateAccessRequest = { AccessRequestCreated("r1", "mda_x", AccessRequestState.PENDING) }
        val viewModel = viewModel()
        viewModel.initialize(link)

        viewModel.onUiEvent(UiEvent.RequestAccessClicked)
        viewModel.onUiEvent(UiEvent.NameChanged(" Anna "))
        viewModel.onUiEvent(UiEvent.SubmitClicked)

        assertEquals(Step.Waiting, viewModel.uiState.value.step)
        assertEquals("createAccessRequest:Anna:null:doc:sender", api.calls.last())
        assertEquals("mda_x", requestStore.get()?.token)
    }

    @Test
    fun `coming back to the link goes on waiting`() = runTest {
        requestStore.put(PendingAccessRequest(backendUrl, "mda_x", link, "Wiki"))
        val viewModel = viewModel()

        viewModel.initialize(link)

        assertEquals(Step.Waiting, viewModel.uiState.value.step)
    }

    @Test
    fun `an approval continues with the invite and keeps the request until the account exists`() = runTest {
        requestStore.put(PendingAccessRequest(backendUrl, "mda_x", link, "Wiki"))
        api.accessRequestStatus = AccessRequestStatus(AccessRequestState.APPROVED, inviteToken = "mdl_invite")
        val viewModel = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        viewModel.initialize(link)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pollRequest() }
        testScheduler.runCurrent()

        val invite = events.first() as JoinEvent.OpenInvite
        assertEquals("mdl_invite", invite.token)
        assertNotNull(requestStore.get())
        pendingJoin.release(invite.backendId)
        assertEquals("doc", pendingJoin.open.value?.documentId)
    }

    @Test
    fun `a used invite leaves only signing in`() = runTest {
        requestStore.put(PendingAccessRequest(backendUrl, "mda_x", link, "Wiki"))
        api.accessRequestStatus = AccessRequestStatus(AccessRequestState.APPROVED)
        val viewModel = viewModel()
        viewModel.initialize(link)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pollRequest() }
        testScheduler.runCurrent()

        assertEquals(Step.Welcome, viewModel.uiState.value.step)
        assertNull(requestStore.get())
    }

    @Test
    fun `a rejection ends the request`() = runTest {
        requestStore.put(PendingAccessRequest(backendUrl, "mda_x", link, "Wiki"))
        api.accessRequestStatus = AccessRequestStatus(AccessRequestState.REJECTED)
        val viewModel = viewModel()
        viewModel.initialize(link)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.pollRequest() }
        testScheduler.runCurrent()

        assertEquals(Problem.Rejected, viewModel.uiState.value.problem)
        assertNull(requestStore.get())
    }

    @Test
    fun `signing in afterwards forgets the request`() = runTest {
        signedInBackend()
        requestStore.put(PendingAccessRequest(backendUrl, "mda_x", link, "Wiki"))

        viewModel().initialize(link)

        assertNull(requestStore.get())
    }

    @Test
    fun `no button for a server without access requests`() = runTest {
        info = ServerInfo(name = "Wiki")
        val viewModel = viewModel()

        viewModel.initialize(link)

        assertEquals(false, viewModel.uiState.value.canRequestAccess)
    }
}
