package de.markusressel.mkdocseditor.feature.auth.link

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.AuthStatus
import de.markusressel.mkdocseditor.auth.api.LinkInfo
import de.markusressel.mkdocseditor.auth.api.PasskeySupport
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.link.LinkKind
import de.markusressel.mkdocseditor.auth.okResponse
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.UnusedCredentialClient
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel.LinkEvent
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel.UiEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthLinkViewModelTest {

    private val token = "mdl_" + "b".repeat(43)
    private val api = FakeAuthApi()
    private val store = InMemoryTokenStore()
    private lateinit var backends: FakeAuthBackends
    private lateinit var viewModel: AuthLinkViewModel
    private val events = mutableListOf<LinkEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api.status = AuthStatus(passkeys = PasskeySupport(enabled = true))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start(link: String, backendId: Long?, vararg configs: de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig) {
        backends = FakeAuthBackends(*configs)
        viewModel = AuthLinkViewModel(AuthRepository(store, { api }, deviceName = "Pixel 8"), backends, UnusedCredentialClient)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
        viewModel.initialize(link, backendId)
    }

    private fun fillAccount(username: String = "anna", password: String = "a long passphrase") {
        viewModel.onUiEvent(UiEvent.UsernameChanged(username))
        viewModel.onUiEvent(UiEvent.PasswordChanged(password))
        viewModel.onUiEvent(UiEvent.PasswordConfirmationChanged(password))
    }

    @Test
    fun `invite link with a known backend creates the account`() = runTest {
        api.linkInfo = LinkInfo(kind = "invite", role = "editor", note = "for Anna")
        api.onRedeem = { _, username, _ -> okResponse(username = username!!) }
        start("https://edit.example.com/invite#$token", backendId = 1, backendConfig(1))

        val form = viewModel.uiState.value
        assertEquals(Step.Form, form.step)
        assertEquals(LinkKind.Invite, form.kind)
        assertEquals("editor", form.role)
        assertTrue(form.passkeysEnabled)

        fillAccount()
        viewModel.onUiEvent(UiEvent.DisplayNameChanged("Anna"))
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)

        assertEquals("redeem:anna:Anna", api.calls.last())
        assertEquals(LinkEvent.SavePassword("anna", "a long passphrase"), events.single())
        assertEquals(Step.Done, viewModel.uiState.value.step)
        assertEquals("anna", viewModel.uiState.value.signedInUsername)
        assertEquals("mds_token", store.sessions[1]?.token)
        assertEquals(1L, backends.selectedId)
        assertEquals("anna", backends.hints[1])
    }

    @Test
    fun `inputs are checked before sending`() = runTest {
        start("mkdocseditor://invite#$token", backendId = 1, backendConfig(1))

        fillAccount(username = "-bad name")
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)
        assertEquals(R.string.auth_error_invalid_username, viewModel.uiState.value.error?.resId)

        fillAccount(password = "short")
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)
        assertEquals(R.string.auth_error_password_too_short, viewModel.uiState.value.error?.resId)

        fillAccount()
        viewModel.onUiEvent(UiEvent.PasswordConfirmationChanged("something else"))
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)
        assertEquals(R.string.auth_error_password_mismatch, viewModel.uiState.value.error?.resId)

        assertTrue(api.calls.none { it.startsWith("redeem") })
    }

    @Test
    fun `server errors are shown`() = runTest {
        api.onRedeem = { _, _, _ -> throw AuthApiException(409, AuthErrorCode.USERNAME_TAKEN, "taken") }
        start(token, backendId = 1, backendConfig(1))
        fillAccount()
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)
        assertEquals(R.string.auth_error_username_taken, viewModel.uiState.value.error?.resId)
        assertEquals(Step.Form, viewModel.uiState.value.step)
    }

    @Test
    fun `fresh install asks for the backend url`() = runTest {
        api.linkInfo = LinkInfo(kind = "setup", role = "admin")
        start("https://edit.example.com/setup#$token", backendId = null)

        assertEquals(Step.ChooseBackend, viewModel.uiState.value.step)
        viewModel.onUiEvent(UiEvent.BackendUrlChanged("backend.wiki.example.com"))
        viewModel.onUiEvent(UiEvent.ContinueClicked)

        assertEquals("backend.wiki.example.com", backends.created.single().host)
        assertEquals(443, backends.created.single().port)
        assertEquals(Step.Form, viewModel.uiState.value.step)
        assertEquals(LinkKind.Setup, viewModel.uiState.value.kind)
    }

    @Test
    fun `an existing backend can be picked`() = runTest {
        start("https://edit.example.com/invite#$token", backendId = null, backendConfig(1, "One"), backendConfig(2, "Two"))
        assertEquals(Step.ChooseBackend, viewModel.uiState.value.step)
        viewModel.onUiEvent(UiEvent.BackendSelected(2))
        viewModel.onUiEvent(UiEvent.ContinueClicked)
        assertEquals("Two", viewModel.uiState.value.backendName)
        assertTrue(backends.created.isEmpty())
    }

    @Test
    fun `reset link sends no username`() = runTest {
        api.linkInfo = LinkInfo(kind = "reset", username = "anna", displayName = "Anna", resetSecondFactors = true)
        api.onRedeem = { _, username, _ ->
            assertEquals(null, username)
            okResponse(username = "anna")
        }
        start("https://edit.example.com/reset#$token", backendId = 1, backendConfig(1))
        assertEquals("anna", viewModel.uiState.value.resetUsername)
        assertTrue(viewModel.uiState.value.resetSecondFactors)

        viewModel.onUiEvent(UiEvent.PasswordChanged("a new long passphrase"))
        viewModel.onUiEvent(UiEvent.PasswordConfirmationChanged("a new long passphrase"))
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)

        assertEquals(LinkEvent.SavePassword("anna", "a new long passphrase"), events.single())
        assertEquals(Step.Done, viewModel.uiState.value.step)
    }

    @Test
    fun `account with a passkey`() = runTest {
        api.onRedeem = { ceremonyId, _, credential ->
            assertEquals("cer", ceremonyId)
            assertEquals("{\"id\":\"new\"}", credential)
            okResponse(username = "anna")
        }
        start(token, backendId = 1, backendConfig(1))
        viewModel.onUiEvent(UiEvent.UsernameChanged("anna"))
        viewModel.onUiEvent(UiEvent.CreatePasskeyClicked)

        assertEquals(LinkEvent.CreatePasskey(api.ceremony.publicKey), events.single())
        assertTrue("beginLinkPasskey:anna" in api.calls)

        viewModel.onUiEvent(UiEvent.PasskeyCreated("{\"id\":\"new\"}"))
        assertEquals(Step.Done, viewModel.uiState.value.step)
        assertTrue(events.none { it is LinkEvent.SavePassword })
    }

    @Test
    fun `cancelled passkey creation keeps the form`() = runTest {
        start(token, backendId = 1, backendConfig(1))
        viewModel.onUiEvent(UiEvent.UsernameChanged("anna"))
        viewModel.onUiEvent(UiEvent.CreatePasskeyClicked)
        viewModel.onUiEvent(UiEvent.PasskeyCreated(null))
        assertEquals(Step.Form, viewModel.uiState.value.step)
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test
    fun `unreadable link`() = runTest {
        start("https://example.com/nothing", backendId = null)
        assertEquals(Step.Invalid, viewModel.uiState.value.step)
    }

    @Test
    fun `done opens the account screen when the backend wants a second factor`() = runTest {
        api.onRedeem = { _, username, _ -> okResponse(username = username!!, secondFactorSetupRequired = true) }
        start("https://edit.example.com/invite#$token", backendId = 1, backendConfig(1))
        fillAccount()
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)
        viewModel.onUiEvent(UiEvent.DoneClicked)
        assertEquals(LinkEvent.OpenAccount(1), events.last())
    }

    @Test
    fun `done closes the flow otherwise`() = runTest {
        api.onRedeem = { _, username, _ -> okResponse(username = username!!) }
        start("https://edit.example.com/invite#$token", backendId = 1, backendConfig(1))
        fillAccount()
        viewModel.onUiEvent(UiEvent.SubmitPasswordClicked)
        viewModel.onUiEvent(UiEvent.DoneClicked)
        assertEquals(LinkEvent.Close, events.last())
    }
}
