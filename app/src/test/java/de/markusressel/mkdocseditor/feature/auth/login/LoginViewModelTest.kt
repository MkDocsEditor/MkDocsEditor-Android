package de.markusressel.mkdocseditor.feature.auth.login

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.AuthStatus
import de.markusressel.mkdocseditor.auth.api.LoginResponse
import de.markusressel.mkdocseditor.auth.api.PasskeySupport
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.okResponse
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.UnusedCredentialClient
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.auth.common.data.PickedCredential
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel.LoginEvent
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel.UiEvent
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginViewModelTest {

    private val api = FakeAuthApi()
    private val store = InMemoryTokenStore()
    private val backends = FakeAuthBackends(backendConfig(1)).apply { hints[1] = "markus" }
    private lateinit var viewModel: LoginViewModel
    private val events = mutableListOf<LoginEvent>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        api.status = AuthStatus(passkeys = PasskeySupport(enabled = true, rpId = "example.com"))
        viewModel = LoginViewModel(
            AuthRepository(store, { api }, deviceName = "Pixel 8"),
            backends,
            UnusedCredentialClient,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.start() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.events.collect { events += it }
        }
        viewModel.initialize(1)
    }

    private fun secondFactorRequired(vararg methods: String) =
        LoginResponse(status = "second-factor-required", challenge = "mdc_1", methods = methods.toList())

    @Test
    fun `prefills the username and reads the status`() = runTest {
        start()
        val state = viewModel.uiState.value
        assertEquals("markus", state.username)
        assertEquals("Wiki", state.backendName)
        assertTrue(state.passkeysEnabled)
    }

    @Test
    fun `password login signs in and offers to save the password`() = runTest {
        api.onLogin = { _, _ -> okResponse(username = "markus") }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("correct horse battery"))
        viewModel.onUiEvent(UiEvent.LoginClicked)

        assertEquals(LoginEvent.SavePassword("markus", "correct horse battery"), events[0])
        assertEquals(LoginEvent.SignedIn(1, false), events[1])
        assertEquals("mds_token", store.sessions[1]?.token)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `wrong password shows an error`() = runTest {
        api.onLogin = { _, _ -> throw AuthApiException(401, AuthErrorCode.INVALID_CREDENTIALS, "no") }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("wrong"))
        viewModel.onUiEvent(UiEvent.LoginClicked)

        assertEquals(R.string.auth_error_invalid_credentials, viewModel.uiState.value.error?.resId)
        assertFalse(viewModel.uiState.value.loading)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `totp code is submitted once it has 6 digits`() = runTest {
        api.onLogin = { _, _ -> secondFactorRequired("totp", "recovery-code") }
        var sentCode: String? = null
        api.onSecondFactor = { _, code, _ ->
            sentCode = code
            okResponse()
        }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("pw"))
        viewModel.onUiEvent(UiEvent.LoginClicked)
        assertEquals(LoginViewModel.Step.SecondFactor, viewModel.uiState.value.step)
        assertFalse(viewModel.uiState.value.useRecoveryCode)

        // pasted with a space: not complete yet
        viewModel.onUiEvent(UiEvent.CodeChanged("123 45"))
        assertNull(sentCode)
        assertEquals("12345", viewModel.uiState.value.code)

        viewModel.onUiEvent(UiEvent.CodeChanged("123 456"))
        assertEquals("123456", sentCode)
        assertIs<LoginEvent.SignedIn>(events.last())
    }

    @Test
    fun `wrong code keeps the second factor step`() = runTest {
        api.onLogin = { _, _ -> secondFactorRequired("totp") }
        api.onSecondFactor = { _, _, _ ->
            throw AuthApiException(401, AuthErrorCode.INVALID_SECOND_FACTOR, "no", details = mapOf("attemptsLeft" to 4.0))
        }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("pw"))
        viewModel.onUiEvent(UiEvent.LoginClicked)
        viewModel.onUiEvent(UiEvent.CodeChanged("000000"))

        val state = viewModel.uiState.value
        assertEquals(LoginViewModel.Step.SecondFactor, state.step)
        assertEquals(R.string.auth_error_invalid_code_attempts, state.error?.resId)
        assertEquals(listOf<Any>(4), state.error?.args)
    }

    @Test
    fun `expired challenge goes back to the password`() = runTest {
        api.onLogin = { _, _ -> secondFactorRequired("totp") }
        api.onSecondFactor = { _, _, _ -> throw AuthApiException(401, AuthErrorCode.CHALLENGE_EXPIRED, "late") }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("pw"))
        viewModel.onUiEvent(UiEvent.LoginClicked)
        viewModel.onUiEvent(UiEvent.CodeChanged("123456"))

        assertEquals(LoginViewModel.Step.Credentials, viewModel.uiState.value.step)
        assertEquals(R.string.auth_error_challenge_expired, viewModel.uiState.value.error?.resId)
    }

    @Test
    fun `recovery code only accounts start with the recovery code`() = runTest {
        api.onLogin = { _, _ -> secondFactorRequired("recovery-code") }
        var recovery: String? = null
        api.onSecondFactor = { _, _, recoveryCode ->
            recovery = recoveryCode
            okResponse()
        }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("pw"))
        viewModel.onUiEvent(UiEvent.LoginClicked)
        assertTrue(viewModel.uiState.value.useRecoveryCode)

        viewModel.onUiEvent(UiEvent.CodeChanged("abcd-efgh-jkmn"))
        assertNull(recovery)
        viewModel.onUiEvent(UiEvent.SubmitCodeClicked)
        assertEquals("abcd-efgh-jkmn", recovery)
    }

    @Test
    fun `passkey login`() = runTest {
        api.onFinishPasskeyLogin = { ceremonyId, credential ->
            assertEquals("cer", ceremonyId)
            assertEquals("{\"id\":\"cred\"}", credential)
            okResponse()
        }
        start()
        viewModel.onUiEvent(UiEvent.PasskeyClicked)

        assertEquals(LoginEvent.PickCredential(api.ceremony.publicKey, includePasswords = true), events.single())
        assertTrue(viewModel.uiState.value.loading)

        viewModel.onUiEvent(UiEvent.CredentialPicked(PickedCredential.Passkey("{\"id\":\"cred\"}")))
        assertEquals(LoginEvent.SignedIn(1, false), events.last())
        // nothing to save for a passkey
        assertTrue(events.none { it is LoginEvent.SavePassword })
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `passkey as second factor sends the challenge and no passwords`() = runTest {
        api.onLogin = { _, _ -> secondFactorRequired("totp", "passkey") }
        start()
        viewModel.onUiEvent(UiEvent.PasswordChanged("pw"))
        viewModel.onUiEvent(UiEvent.LoginClicked)
        viewModel.onUiEvent(UiEvent.PasskeyClicked)

        assertTrue("beginPasskeyLogin:mdc_1" in api.calls)
        assertEquals(LoginEvent.PickCredential(api.ceremony.publicKey, includePasswords = false), events.last())
    }

    @Test
    fun `a saved password from the provider signs in without a save prompt`() = runTest {
        var usedPassword: String? = null
        api.onLogin = { _, password ->
            usedPassword = password
            okResponse(username = "anna")
        }
        start()
        viewModel.onUiEvent(UiEvent.PasskeyClicked)
        viewModel.onUiEvent(UiEvent.CredentialPicked(PickedCredential.Password("anna", "from bitwarden")))

        assertEquals("from bitwarden", usedPassword)
        assertEquals(LoginEvent.SignedIn(1, false), events.last())
        assertTrue(events.none { it is LoginEvent.SavePassword })
        assertEquals("anna", backends.hints[1])
    }

    @Test
    fun `cancelled credential sheet ends loading`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.PasskeyClicked)
        viewModel.onUiEvent(UiEvent.CredentialPicked(null))
        assertFalse(viewModel.uiState.value.loading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `pasted link opens the link flow`() = runTest {
        start()
        val token = "mdl_" + "a".repeat(43)
        viewModel.onUiEvent(UiEvent.LinkInputChanged("https://edit.example.com/invite#$token"))
        viewModel.onUiEvent(UiEvent.OpenLinkClicked)
        val event = assertIs<LoginEvent.OpenLink>(events.single())
        assertEquals(token, event.link.token)

        viewModel.onUiEvent(UiEvent.LinkInputChanged("nonsense"))
        viewModel.onUiEvent(UiEvent.OpenLinkClicked)
        assertEquals(R.string.auth_error_link_unreadable, viewModel.uiState.value.error?.resId)
    }
}
