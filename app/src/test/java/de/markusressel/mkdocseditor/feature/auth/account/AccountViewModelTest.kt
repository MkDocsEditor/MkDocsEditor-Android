package de.markusressel.mkdocseditor.feature.auth.account

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AccountInfo
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import de.markusressel.mkdocseditor.auth.api.SessionInfo
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.UnusedCredentialClient
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.AccountEvent
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.Dialog
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.auth.backendConfig
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

class AccountViewModelTest {

    private val api = FakeAuthApi()
    private val store = InMemoryTokenStore()
    private val backends = FakeAuthBackends(backendConfig(1))
    private lateinit var viewModel: AccountViewModel
    private val events = mutableListOf<AccountEvent>()

    private var account = AccountInfo(
        id = "u1",
        username = "markus",
        displayName = "Markus",
        role = "editor",
        hasPassword = true,
        passkeyCount = 1,
    )

    private fun reauthRequired() = AuthApiException(403, AuthErrorCode.REAUTH_REQUIRED, "reauth required")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        store.put(1, AuthSession("mds_token", expiresAt = null, username = "markus"))
        api.onAccount = { account }
        api.onPasskeys = { listOf(PasskeyInfo("p1", "Bitwarden", backedUp = true)) }
        api.onSessions = {
            listOf(SessionInfo(id = "s2", deviceName = "Firefox"), SessionInfo(id = "s1", deviceName = "Pixel", current = true))
        }
        viewModel = AccountViewModel(
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

    @Test
    fun `loads account, passkeys and sessions with the current session first`() = runTest {
        start()
        val state = viewModel.uiState.value
        assertEquals("markus", state.account?.username)
        assertEquals("Markus", state.displayName)
        assertEquals(listOf("p1"), state.passkeys.map { it.id })
        assertEquals(listOf("s1", "s2"), state.sessions.map { it.id })
        assertFalse(state.loading)
    }

    @Test
    fun `without a session the login is required`() = runTest {
        store.remove(1)
        start()
        assertEquals(listOf<AccountEvent>(AccountEvent.LoginRequired(1)), events)
    }

    @Test
    fun `a rejected session asks for the login and drops the token`() = runTest {
        api.onAccount = { throw AuthApiException(401, AuthErrorCode.UNAUTHENTICATED, "expired") }
        start()
        assertEquals(listOf<AccountEvent>(AccountEvent.LoginRequired(1)), events)
        assertNull(store.sessions[1])
    }

    @Test
    fun `saving the profile sends only changed fields, an empty email removes it`() = runTest {
        account = account.copy(email = "old@example.com")
        start()
        viewModel.onUiEvent(UiEvent.DisplayNameChanged("Markus R"))
        viewModel.onUiEvent(UiEvent.EmailChanged(""))
        viewModel.onUiEvent(UiEvent.SaveProfileClicked)
        assertTrue(api.calls.contains("updateAccount:AccountUpdate(displayName=Markus R, color=null, email=, username=null)"))
        assertEquals(R.string.account_profile_saved, viewModel.uiState.value.info?.resId)
    }

    @Test
    fun `changing the password with the current one offers to save the new one`() = runTest {
        var sent: Pair<String?, String>? = null
        api.onChangePassword = { current, new -> sent = current to new }
        start()
        viewModel.onUiEvent(UiEvent.ChangePasswordClicked)
        viewModel.onUiEvent(UiEvent.ChangePasswordInput("old password", "new long passphrase", "new long passphrase"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)

        assertEquals("old password" to "new long passphrase", sent)
        assertEquals(AccountEvent.SavePassword("markus", "new long passphrase"), events.single())
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a mismatching or short new password is rejected locally`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.ChangePasswordClicked)
        viewModel.onUiEvent(UiEvent.ChangePasswordInput("old", "short", "short"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)
        assertEquals(R.string.auth_error_password_too_short, (viewModel.uiState.value.dialog as Dialog.ChangePassword).error?.resId)

        viewModel.onUiEvent(UiEvent.ChangePasswordInput("old", "long enough passphrase", "something else entirely"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)
        assertEquals(R.string.auth_error_password_mismatch, (viewModel.uiState.value.dialog as Dialog.ChangePassword).error?.resId)
        assertFalse(api.calls.contains("changePassword"))
    }

    @Test
    fun `reauth-required asks for a step-up and retries the action once`() = runTest {
        account = account.copy(totpEnabled = true)
        var attempts = 0
        api.onChangePassword = { _, _ -> if (attempts++ == 0) throw reauthRequired() }
        start()
        viewModel.onUiEvent(UiEvent.ChangePasswordClicked)
        viewModel.onUiEvent(UiEvent.ChangePasswordInput("", "new long passphrase", "new long passphrase"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)

        val stepUp = assertIs<Dialog.StepUp>(viewModel.uiState.value.dialog)
        assertTrue(stepUp.needsCode)
        assertTrue(stepUp.passkeyAvailable)

        viewModel.onUiEvent(UiEvent.StepUpInput("old password", "123 456"))
        viewModel.onUiEvent(UiEvent.StepUpConfirmed)

        assertTrue(api.calls.contains("reauth:old password:123456"))
        assertEquals(2, attempts)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(AccountEvent.SavePassword("markus", "new long passphrase"), events.single())
    }

    @Test
    fun `a failed step-up keeps the dialog open with the error`() = runTest {
        api.onChangePassword = { _, _ -> throw reauthRequired() }
        api.onReauth = { _, _ -> throw AuthApiException(401, AuthErrorCode.INVALID_CREDENTIALS, "wrong") }
        start()
        viewModel.onUiEvent(UiEvent.ChangePasswordClicked)
        viewModel.onUiEvent(UiEvent.ChangePasswordInput("", "new long passphrase", "new long passphrase"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)
        viewModel.onUiEvent(UiEvent.StepUpInput("wrong", ""))
        viewModel.onUiEvent(UiEvent.StepUpConfirmed)

        val stepUp = assertIs<Dialog.StepUp>(viewModel.uiState.value.dialog)
        assertEquals(R.string.auth_error_invalid_credentials, stepUp.error?.resId)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `step-up with a passkey asks Credential Manager for passkeys only, then retries`() = runTest {
        var attempts = 0
        api.onChangePassword = { _, _ -> if (attempts++ == 0) throw reauthRequired() }
        start()
        viewModel.onUiEvent(UiEvent.ChangePasswordClicked)
        viewModel.onUiEvent(UiEvent.ChangePasswordInput("", "new long passphrase", "new long passphrase"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)
        viewModel.onUiEvent(UiEvent.StepUpWithPasskeyClicked)

        assertEquals(AccountEvent.GetPasskey(api.ceremony.publicKey), events.single())
        viewModel.onUiEvent(UiEvent.PasskeyResult("""{"id":"cred"}"""))

        assertTrue(api.calls.contains("finishReauthPasskey"))
        assertEquals(2, attempts)
        assertIs<AccountEvent.SavePassword>(events.last())
    }

    @Test
    fun `TOTP enrolment accepts pasted codes with spaces and shows the recovery codes once`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.EnableTotpClicked)
        val enroll = assertIs<Dialog.TotpEnroll>(viewModel.uiState.value.dialog)
        assertTrue(enroll.enrollment.otpauthUri.startsWith("otpauth://"))

        viewModel.onUiEvent(UiEvent.TotpCodeChanged("123 456"))
        assertEquals("123456", (viewModel.uiState.value.dialog as Dialog.TotpEnroll).code)
        assertFalse(api.calls.any { it.startsWith("confirmTotp") }, "no auto-submit")

        viewModel.onUiEvent(UiEvent.ConfirmTotpClicked)
        assertTrue(api.calls.contains("confirmTotp:123456"))
        val codes = assertIs<Dialog.RecoveryCodes>(viewModel.uiState.value.dialog)
        assertEquals(10, codes.codes.size)

        viewModel.onUiEvent(UiEvent.DismissDialog)
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `a wrong TOTP code stays in the enrolment dialog`() = runTest {
        api.onConfirmTotp = { throw AuthApiException(400, AuthErrorCode.INVALID_SECOND_FACTOR, "wrong") }
        start()
        viewModel.onUiEvent(UiEvent.EnableTotpClicked)
        viewModel.onUiEvent(UiEvent.TotpCodeChanged("12345"))
        viewModel.onUiEvent(UiEvent.ConfirmTotpClicked)
        assertEquals(R.string.auth_error_code_length, (viewModel.uiState.value.dialog as Dialog.TotpEnroll).error?.resId)

        viewModel.onUiEvent(UiEvent.TotpCodeChanged("654321"))
        viewModel.onUiEvent(UiEvent.ConfirmTotpClicked)
        val enroll = assertIs<Dialog.TotpEnroll>(viewModel.uiState.value.dialog)
        assertEquals(R.string.auth_error_invalid_code, enroll.error?.resId)
    }

    @Test
    fun `adding a passkey goes through Credential Manager`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.AddPasskeyClicked)
        assertEquals(AccountEvent.CreatePasskey(api.ceremony.publicKey), events.single())
        assertTrue(viewModel.uiState.value.loading)

        viewModel.onUiEvent(UiEvent.PasskeyResult("""{"id":"new"}"""))
        assertTrue(api.calls.contains("finishAddPasskey:null"))
        assertEquals(R.string.account_passkey_added, viewModel.uiState.value.info?.resId)
        assertFalse(viewModel.uiState.value.loading)
    }

    @Test
    fun `a cancelled passkey sheet ends the loading state`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.AddPasskeyClicked)
        viewModel.onUiEvent(UiEvent.PasskeyResult(null))
        assertFalse(viewModel.uiState.value.loading)
        assertFalse(api.calls.any { it.startsWith("finishAddPasskey") })
    }

    @Test
    fun `deleting a passkey asks first`() = runTest {
        start()
        val passkey = viewModel.uiState.value.passkeys.single()
        viewModel.onUiEvent(UiEvent.DeletePasskeyClicked(passkey))
        assertIs<Dialog.ConfirmDeletePasskey>(viewModel.uiState.value.dialog)
        viewModel.onUiEvent(UiEvent.DeletePasskeyConfirmed)
        assertTrue(api.calls.contains("deletePasskey:p1"))
        assertNull(viewModel.uiState.value.dialog)
    }

    @Test
    fun `revoking another session keeps this one, revoking the current one signs out`() = runTest {
        start()
        val (current, other) = viewModel.uiState.value.sessions
        viewModel.onUiEvent(UiEvent.RevokeSessionClicked(other))
        assertTrue(api.calls.contains("revokeSession:s2"))
        assertEquals("mds_token", store.sessions[1]?.token)

        viewModel.onUiEvent(UiEvent.RevokeSessionClicked(current))
        assertTrue(api.calls.contains("logout:mds_token"))
        assertNull(store.sessions[1])
        assertEquals(AccountEvent.SignedOut, events.last())
    }

    @Test
    fun `sign out of all other devices reports the count`() = runTest {
        start()
        viewModel.onUiEvent(UiEvent.RevokeOtherSessionsClicked)
        assertTrue(api.calls.contains("revokeOthers"))
        val info = viewModel.uiState.value.info
        assertEquals(R.string.account_sessions_revoked, info?.resId)
        assertEquals(listOf<Any>(2), info?.args)
    }

    @Test
    fun `a second reauth-required after the step-up is shown, not retried again`() = runTest {
        var attempts = 0
        api.onChangePassword = { _, _ -> attempts++; throw reauthRequired() }
        start()
        viewModel.onUiEvent(UiEvent.ChangePasswordClicked)
        viewModel.onUiEvent(UiEvent.ChangePasswordInput("", "new long passphrase", "new long passphrase"))
        viewModel.onUiEvent(UiEvent.ChangePasswordConfirmed)
        viewModel.onUiEvent(UiEvent.StepUpInput("password", ""))
        viewModel.onUiEvent(UiEvent.StepUpConfirmed)

        assertEquals(2, attempts)
        assertNull(viewModel.uiState.value.dialog)
        assertEquals(R.string.auth_error_reauth_required, viewModel.uiState.value.error?.resId)
    }
}
