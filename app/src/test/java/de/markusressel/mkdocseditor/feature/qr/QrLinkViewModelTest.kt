package de.markusressel.mkdocseditor.feature.qr

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.requests.DefaultBody
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AccountRole
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AccountRoleStore
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.editor.domain.WikiTreeHolder
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.doc
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import de.markusressel.mkdocseditor.feature.qr.data.PendingQrKey
import de.markusressel.mkdocseditor.feature.qr.domain.QrPage
import de.markusressel.mkdocseditor.feature.qr.domain.ResolveQrCodeUseCase
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.Problem
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.QrLinkEvent
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.UiEvent
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.qr.QrCodeModel
import de.markusressel.mkdocsrestclient.reference.ItemRefModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class QrLinkViewModelTest {

    private val link = "https://wiki.markusressel.de/q/wm"

    private class MapStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private val tokens = InMemoryTokenStore()
    private val roles = AccountRoleStore(MapStore())
    private val backends = FakeAuthBackends()
    private val navigation = MainNavigationRequests()
    private val pendingQrKey = PendingQrKey()
    private val events = mutableListOf<QrLinkEvent>()
    private val requests = mutableListOf<Pair<String, Boolean>>()
    private var answer: Result<QrCodeModel, FuelError> = Result.error(FuelError.wrap(RuntimeException()))

    private val tree = section(
        "root", "", documents = listOf(doc("d-wm", "waschmaschine")),
        subsections = listOf(section("s-haus", "haus", documents = listOf(doc("d-heizung", "heizung")))),
    )

    private fun viewModel() = QrLinkViewModel(
        resolve = ResolveQrCodeUseCase(object : IMkDocsRestClient by DummyMkDocsRestClient() {
            override suspend fun getQrCode(key: String, scan: Boolean): Result<QrCodeModel, FuelError> {
                requests += key to scan
                return answer
            }
        }),
        backends = backends,
        authRepository = AuthRepository(tokens, { FakeAuthApi() }, deviceName = "Pixel 8", roleStore = roles),
        treeHolder = WikiTreeHolder({ tree }, { tree }),
        pendingQrKey = pendingQrKey,
        navigation = navigation,
    )

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun signedIn(role: String = AccountRole.EDITOR) {
        backends.configs += backendConfig(1)
        backends.selectedId = 1
        tokens.put(1, AuthSession("t", null, "anna"))
        roles.put(1, "anna", role)
    }

    private fun httpError(status: Int, body: String) = Result.error(
        FuelError.wrap(
            RuntimeException("HTTP $status"),
            Response(
                url = URL("http://localhost/qr/wm/"),
                statusCode = status,
                body = DefaultBody.from({ ByteArrayInputStream(body.toByteArray()) }, { body.length.toLong() }),
            ),
        )
    )

    private fun kotlinx.coroutines.test.TestScope.collectEvents(viewModel: QrLinkViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }

    @Test
    fun `a sticker opens its page and counts as a scan`() = runTest {
        signedIn()
        answer = Result.success(QrCodeModel("wm", null, ItemRefModel("document", "d-wm", "waschmaschine.md", "waschmaschine"), "WM"))
        val viewModel = viewModel()
        collectEvents(viewModel)

        viewModel.initialize(link)

        assertEquals(listOf("wm" to true), requests)
        assertEquals("d-wm", navigation.documentToOpen.value?.documentId)
        assertEquals(listOf<QrLinkEvent>(QrLinkEvent.Close), events)
    }

    @Test
    fun `a key on several pages lists them to pick from`() = runTest {
        signedIn()
        answer = httpError(
            409,
            """{"code":"qr-conflict","details":{"key":"wm","items":[{"item":{"type":"document","id":"d1","path":"a.md","name":"a"},"title":"A"}]}}""",
        )
        val viewModel = viewModel()
        collectEvents(viewModel)
        viewModel.initialize(link)

        assertEquals(Step.Conflict, viewModel.uiState.value.step)
        assertEquals(listOf(QrPage("d1", "A", "a.md")), viewModel.uiState.value.pages)

        viewModel.onUiEvent(UiEvent.PageClicked("d1"))
        assertEquals("d1", navigation.documentToOpen.value?.documentId)
        assertEquals(listOf<QrLinkEvent>(QrLinkEvent.Close), events)
    }

    @Test
    fun `a key without a page goes on the page an editor picks`() = runTest {
        signedIn()
        answer = httpError(410, """{"code":"qr-gone","details":{"key":"wm","lastPath":"alt/wm.md"}}""")
        val viewModel = viewModel()
        collectEvents(viewModel)
        viewModel.initialize(link)

        assertEquals(Step.NoPage, viewModel.uiState.value.step)
        assertEquals("alt/wm.md", viewModel.uiState.value.lastPath)
        assertTrue(viewModel.uiState.value.canPutOnPage)

        viewModel.onUiEvent(UiEvent.PutOnPageClicked)
        assertEquals(Step.PickPage, viewModel.uiState.value.step)
        viewModel.onUiEvent(UiEvent.QueryChanged("haus hei"))
        assertEquals(listOf(QrPage("d-heizung", "heizung", "haus/heizung.md")), viewModel.uiState.value.shownPages)

        viewModel.onUiEvent(UiEvent.TargetPageClicked("d-heizung"))
        val request = pendingQrKey.request.value
        assertEquals("d-heizung" to "wm", request?.documentId to request?.key)
        assertEquals("d-heizung", navigation.documentToOpen.value?.documentId)
    }

    @Test
    fun `a reader can't put a key on a page`() = runTest {
        signedIn(role = AccountRole.READER)
        answer = httpError(404, """{"code":"not-found","message":"x"}""")
        val viewModel = viewModel()
        viewModel.initialize(link)

        assertEquals(Step.NoPage, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.canPutOnPage)
    }

    @Test
    fun `without a server nothing is asked`() = runTest {
        val viewModel = viewModel()
        viewModel.initialize(link)

        assertEquals(Problem.NoBackend, viewModel.uiState.value.problem)
        assertEquals(emptyList(), requests)
    }

    @Test
    fun `a signed out user is sent to sign in`() = runTest {
        signedIn()
        answer = httpError(401, """{"code":"unauthorized"}""")
        val viewModel = viewModel()
        collectEvents(viewModel)
        viewModel.initialize(link)

        assertEquals(Problem.NotSignedIn, viewModel.uiState.value.problem)
        viewModel.onUiEvent(UiEvent.SignInClicked)
        assertEquals(listOf<QrLinkEvent>(QrLinkEvent.OpenLogin(1)), events)
    }

    @Test
    fun `offline the code can be tried again`() = runTest {
        signedIn()
        answer = Result.error(FuelError.wrap(java.net.ConnectException()))
        val viewModel = viewModel()
        viewModel.initialize(link)
        assertEquals(Problem.Unreachable, viewModel.uiState.value.problem)

        answer = Result.success(QrCodeModel("wm", null, ItemRefModel("document", "d-wm", "waschmaschine.md", "waschmaschine"), "WM"))
        viewModel.onUiEvent(UiEvent.RetryClicked)
        assertEquals("d-wm", navigation.documentToOpen.value?.documentId)
    }
}
