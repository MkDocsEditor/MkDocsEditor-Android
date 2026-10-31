package de.markusressel.mkdocseditor.feature.auth.account.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AccountInfo
import de.markusressel.mkdocseditor.auth.api.AccountUpdate
import de.markusressel.mkdocseditor.auth.api.AuthApi
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import de.markusressel.mkdocseditor.auth.api.SessionInfo
import de.markusressel.mkdocseditor.auth.api.TotpEnrollment
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.NotSignedInException
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.CredentialClient
import de.markusressel.mkdocseditor.feature.auth.common.data.UiMessage
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data.CredentialProviders
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data.PasskeyPromptPolicy
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data.PasskeyPromptStore
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.time.Clock
import javax.inject.Inject

/**
 * The account of the signed-in user on one backend: profile, password, TOTP, recovery codes,
 * passkeys and sessions (docs/auth.md section 9). Sensitive actions ask for a step-up when
 * the server answers `reauth-required`, then run once more.
 */
@HiltViewModel
internal class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backends: AuthBackends,
    internal val credentialClient: CredentialClient,
    private val passkeyPromptStore: PasskeyPromptStore,
    private val credentialProviders: CredentialProviders,
) : ViewModel() {

    /** for tests */
    internal var clock: Clock = Clock.systemUTC()

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<AccountEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var backend: BackendRef? = null

    /** the sensitive action that waits for the step-up */
    private var pendingAction: (suspend () -> Unit)? = null
    private var pendingCeremonyId: String? = null
    private var passkeyPurpose: PasskeyPurpose? = null

    private enum class PasskeyPurpose { StepUp, Register }

    fun initialize(backendId: Long) {
        if (backend?.id == backendId) return
        launch {
            val config = backends.get(backendId)
            val ref = config?.toBackendRef()
            if (config == null || ref == null) {
                showError(UiMessage(R.string.auth_error_backend_missing))
                return@launch
            }
            backend = ref
            _uiState.update { it.copy(backendName = config.name) }
            reload()
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.Refresh -> reload()
                is UiEvent.DismissMessage -> _uiState.update { it.copy(error = null, info = null) }
                is UiEvent.DisplayNameChanged -> _uiState.update { it.copy(displayName = event.text) }
                is UiEvent.EmailChanged -> _uiState.update { it.copy(email = event.text) }
                is UiEvent.ColorChanged -> _uiState.update { it.copy(color = event.color) }
                is UiEvent.SaveProfileClicked -> saveProfile()

                is UiEvent.ChangePasswordClicked -> showDialog(Dialog.ChangePassword())
                is UiEvent.ChangePasswordInput -> updateDialog<Dialog.ChangePassword> {
                    it.copy(current = event.current, new = event.new, confirmation = event.confirmation)
                }

                is UiEvent.ChangePasswordConfirmed -> changePassword()

                is UiEvent.EnableTotpClicked -> sensitive { beginTotp() }
                is UiEvent.TotpCodeChanged -> onTotpCodeChanged(event.text)
                is UiEvent.ConfirmTotpClicked -> confirmTotp()
                is UiEvent.DisableTotpClicked -> showDialog(Dialog.ConfirmDisableTotp)
                is UiEvent.DisableTotpConfirmed -> sensitive {
                    call { disableTotp(it) }
                    dismissDialog()
                    showInfo(UiMessage(R.string.account_totp_disabled))
                    reload()
                }

                is UiEvent.RegenerateRecoveryCodesClicked -> sensitive {
                    val codes = call { regenerateRecoveryCodes(it) }
                    showDialog(Dialog.RecoveryCodes(codes))
                    reload()
                }

                is UiEvent.AddPasskeyClicked -> sensitive { beginAddPasskey() }
                is UiEvent.RenamePasskeyClicked -> showDialog(Dialog.RenamePasskey(event.passkey.id, event.passkey.name))
                is UiEvent.RenamePasskeyInput -> updateDialog<Dialog.RenamePasskey> { it.copy(name = event.name) }
                is UiEvent.RenamePasskeyConfirmed -> renamePasskey()
                is UiEvent.DeletePasskeyClicked -> showDialog(Dialog.ConfirmDeletePasskey(event.passkey.id, event.passkey.name))
                is UiEvent.DeletePasskeyConfirmed -> {
                    val dialog = uiState.value.dialog as? Dialog.ConfirmDeletePasskey ?: return@launch
                    sensitive {
                        call { deletePasskey(it, dialog.id) }
                        dismissDialog()
                        reload()
                    }
                }

                is UiEvent.RevokeSessionClicked -> revokeSession(event.session)
                is UiEvent.RevokeOtherSessionsClicked -> runLoading {
                    val revoked = call { revokeOtherSessions(it) }
                    showInfo(UiMessage(R.string.account_sessions_revoked, listOf(revoked)))
                    reload()
                }

                is UiEvent.SignOutClicked -> signOut()

                is UiEvent.PasskeyReminderDismissed -> {
                    backend?.let { passkeyPromptStore.reminderDismissed(it.id, clock.instant()) }
                    _uiState.update { it.copy(showPasskeyReminder = false) }
                }

                is UiEvent.StepUpInput -> updateDialog<Dialog.StepUp> { it.copy(password = event.password, code = event.code) }
                is UiEvent.StepUpConfirmed -> stepUpWithPassword()
                is UiEvent.StepUpWithPasskeyClicked -> stepUpWithPasskey()

                is UiEvent.PasskeyResult -> onPasskeyResult(event.json)
                is UiEvent.PasskeyFailed -> {
                    pendingCeremonyId = null
                    _uiState.update { it.copy(loading = false, error = event.error.toAuthMessage()) }
                }

                is UiEvent.DismissDialog -> {
                    pendingAction = null
                    dismissDialog()
                }
            }
        }
    }

    private suspend fun reload() {
        runLoading {
            val account = call { account(it) }
            val passkeys = call { passkeys(it) }
            val sessions = call { sessions(it) }
            val showReminder = passkeyReminderDue(passkeys)
            _uiState.update {
                it.copy(
                    showPasskeyReminder = showReminder,
                    account = account,
                    passkeys = passkeys,
                    sessions = sessions.sortedByDescending { session -> session.current },
                    displayName = account.displayName,
                    email = account.email.orEmpty(),
                    color = account.color.orEmpty(),
                )
            }
        }
    }

    /**
     * The card after "Later" on the passkey suggestion, at most every few days
     */
    private suspend fun passkeyReminderDue(passkeys: List<PasskeyInfo>): Boolean {
        val ref = backend ?: return false
        val state = passkeyPromptStore.state(ref.id)
        if (state.skippedAt == null) return false
        val passkeysEnabled = try {
            authRepository.status(ref).passkeys.enabled
        } catch (e: Exception) {
            false
        }
        return PasskeyPromptPolicy.shouldRemind(
            state = state,
            now = clock.instant(),
            passkeysEnabled = passkeysEnabled,
            hasUsablePasskey = PasskeyPromptPolicy.hasUsablePasskey(passkeys, state.registeredHere, credentialProviders::isInstalled),
        )
    }

    private suspend fun saveProfile() {
        val state = uiState.value
        val account = state.account ?: return
        val update = AccountUpdate(
            displayName = state.displayName.trim().takeIf { it != account.displayName },
            email = state.email.trim().takeIf { it != account.email.orEmpty() },
            color = state.color.trim().takeIf { it.isNotEmpty() && it != account.color },
        )
        if (update == AccountUpdate()) return
        runLoading {
            val updated = call { updateAccount(it, update) }
            _uiState.update { it.copy(account = updated) }
            showInfo(UiMessage(R.string.account_profile_saved))
        }
    }

    private suspend fun changePassword() {
        val dialog = uiState.value.dialog as? Dialog.ChangePassword ?: return
        val account = uiState.value.account ?: return
        val minLength = MIN_PASSWORD_LENGTH
        val error = when {
            dialog.new.codePointCount(0, dialog.new.length) < minLength ->
                UiMessage(R.string.auth_error_password_too_short, listOf(minLength))

            dialog.new != dialog.confirmation -> UiMessage(R.string.auth_error_password_mismatch)
            else -> null
        }
        if (error != null) {
            updateDialog<Dialog.ChangePassword> { it.copy(error = error) }
            return
        }
        // with the current password no step-up is needed; without one (passkey-only accounts) it is
        val current = dialog.current.takeIf { it.isNotEmpty() && account.hasPassword }
        sensitive {
            call { changePassword(it, current, dialog.new) }
            dismissDialog()
            _events.send(AccountEvent.SavePassword(account.username, dialog.new))
            showInfo(UiMessage(R.string.account_password_changed))
            reload()
        }
    }

    private suspend fun beginTotp() {
        val enrollment = call { beginTotp(it) }
        showDialog(Dialog.TotpEnroll(enrollment))
    }

    private fun onTotpCodeChanged(text: String) {
        updateDialog<Dialog.TotpEnroll> { it.copy(code = text.filter(Char::isDigit).take(6), error = null) }
    }

    private suspend fun confirmTotp() {
        val dialog = uiState.value.dialog as? Dialog.TotpEnroll ?: return
        if (dialog.code.length != 6) {
            updateDialog<Dialog.TotpEnroll> { it.copy(error = UiMessage(R.string.auth_error_code_length)) }
            return
        }
        runLoading(onError = { error -> updateDialog<Dialog.TotpEnroll> { it.copy(error = error) } }) {
            val codes = try {
                call { confirmTotp(it, dialog.code) }
            } catch (e: AuthApiException) {
                if (e.code != AuthErrorCode.CONFLICT) throw e
                // the pending secret expired (10 minutes) or was replaced: start over
                dismissDialog()
                showError(UiMessage(R.string.account_totp_enrollment_expired))
                return@runLoading
            }
            showDialog(Dialog.RecoveryCodes(codes))
            reload()
        }
    }

    private suspend fun beginAddPasskey() {
        val ceremony = call { beginAddPasskey(it) }
        pendingCeremonyId = ceremony.ceremonyId
        passkeyPurpose = PasskeyPurpose.Register
        _uiState.update { it.copy(loading = true) }
        _events.send(AccountEvent.CreatePasskey(ceremony.publicKey))
    }

    private suspend fun renamePasskey() {
        val dialog = uiState.value.dialog as? Dialog.RenamePasskey ?: return
        val name = dialog.name.trim()
        if (name.isEmpty()) return
        runLoading {
            call { renamePasskey(it, dialog.id, name) }
            dismissDialog()
            reload()
        }
    }

    private suspend fun revokeSession(session: SessionInfo) {
        if (session.current) {
            // revoking the own session is a sign-out
            signOut()
            return
        }
        runLoading {
            call { revokeSession(it, session.id) }
            reload()
        }
    }

    private suspend fun signOut() {
        val ref = backend ?: return
        authRepository.signOut(ref)
        _events.send(AccountEvent.SignedOut)
    }

    /**
     * Runs a sensitive action; asks for a step-up and runs it again when the server wants one
     */
    private suspend fun sensitive(action: suspend () -> Unit) {
        runLoading {
            try {
                action()
            } catch (e: AuthApiException) {
                if (e.code != AuthErrorCode.REAUTH_REQUIRED) throw e
                pendingAction = action
                val account = uiState.value.account
                showDialog(
                    Dialog.StepUp(
                        hasPassword = account?.hasPassword ?: true,
                        needsCode = account?.totpEnabled ?: false,
                        passkeyAvailable = (account?.passkeyCount ?: 0) > 0,
                    )
                )
            }
        }
    }

    private suspend fun stepUpWithPassword() {
        val dialog = uiState.value.dialog as? Dialog.StepUp ?: return
        runLoading(onError = ::onStepUpError) {
            call {
                reauth(
                    it,
                    password = dialog.password.takeIf { dialog.hasPassword },
                    code = dialog.code.filter(Char::isDigit).takeIf { dialog.needsCode },
                )
            }
            runPendingAction()
        }
    }

    private suspend fun stepUpWithPasskey() {
        runLoading(onError = ::onStepUpError) {
            val ceremony = call { beginReauthPasskey(it) }
            pendingCeremonyId = ceremony.ceremonyId
            passkeyPurpose = PasskeyPurpose.StepUp
            _uiState.update { it.copy(loading = true) }
            _events.send(AccountEvent.GetPasskey(ceremony.publicKey))
        }
    }

    /** errors of the step-up go into its dialog, errors of the retried action below the screen title */
    private fun onStepUpError(error: UiMessage) {
        if (uiState.value.dialog is Dialog.StepUp) {
            updateDialog<Dialog.StepUp> { it.copy(error = error) }
        } else {
            showError(error)
        }
    }

    private suspend fun runPendingAction() {
        val action = pendingAction ?: return
        pendingAction = null
        dismissDialog()
        // once: a second reauth-required is shown as an error
        action()
    }

    /**
     * @param json the passkey response of Credential Manager, null when cancelled
     */
    private suspend fun onPasskeyResult(json: String?) {
        val ceremonyId = pendingCeremonyId
        val purpose = passkeyPurpose
        pendingCeremonyId = null
        passkeyPurpose = null
        if (json == null || ceremonyId == null) {
            _uiState.update { it.copy(loading = false) }
            return
        }
        when (purpose) {
            PasskeyPurpose.StepUp -> runLoading(onError = ::onStepUpError) {
                call { finishReauthPasskey(it, ceremonyId, json) }
                runPendingAction()
            }

            PasskeyPurpose.Register -> runLoading {
                val passkey = call { finishAddPasskey(it, ceremonyId, json, name = null) }
                backend?.let { passkeyPromptStore.registered(it.id, passkey.id) }
                showInfo(UiMessage(R.string.account_passkey_added))
                reload()
            }

            null -> _uiState.update { it.copy(loading = false) }
        }
    }

    /**
     * An authenticated call; a rejected session ends in [AccountEvent.LoginRequired]
     */
    private suspend fun <T> call(block: suspend AuthApi.(token: String) -> T): T {
        val ref = backend ?: throw NotSignedInException(-1)
        return authRepository.authenticated(ref, block)
    }

    private suspend fun runLoading(
        onError: ((UiMessage) -> Unit)? = null,
        block: suspend () -> Unit,
    ) {
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Account action failed")
            if (e is NotSignedInException || (e is AuthApiException && e.isUnauthenticated)) {
                _events.send(AccountEvent.LoginRequired(backend?.id ?: return))
                return
            }
            val message = e.toAuthMessage()
            if (onError != null) onError(message) else showError(message)
        } finally {
            if (pendingCeremonyId == null) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun showDialog(dialog: Dialog) = _uiState.update { it.copy(dialog = dialog) }

    private fun dismissDialog() = _uiState.update { it.copy(dialog = null) }

    private inline fun <reified D : Dialog> updateDialog(crossinline block: (D) -> D) {
        _uiState.update { state ->
            val dialog = state.dialog as? D ?: return@update state
            state.copy(dialog = block(dialog))
        }
    }

    private fun showError(message: UiMessage) = _uiState.update { it.copy(error = message) }

    private fun showInfo(message: UiMessage) = _uiState.update { it.copy(info = message) }

    internal sealed interface Dialog {
        data class ChangePassword(
            val current: String = "",
            val new: String = "",
            val confirmation: String = "",
            val error: UiMessage? = null,
        ) : Dialog {
            override fun toString() = "ChangePassword(error=$error)"
        }

        data class TotpEnroll(
            val enrollment: TotpEnrollment,
            val code: String = "",
            val error: UiMessage? = null,
        ) : Dialog {
            override fun toString() = "TotpEnroll(error=$error)"
        }

        data object ConfirmDisableTotp : Dialog

        /**
         * Shown once, right after they were created
         */
        data class RecoveryCodes(val codes: List<String>) : Dialog {
            override fun toString() = "RecoveryCodes(${codes.size})"
        }

        data class RenamePasskey(val id: String, val name: String) : Dialog
        data class ConfirmDeletePasskey(val id: String, val name: String) : Dialog

        data class StepUp(
            val hasPassword: Boolean,
            val needsCode: Boolean,
            val passkeyAvailable: Boolean,
            val password: String = "",
            val code: String = "",
            val error: UiMessage? = null,
        ) : Dialog {
            override fun toString() = "StepUp(needsCode=$needsCode, error=$error)"
        }
    }

    internal data class UiState(
        val backendName: String = "",
        val account: AccountInfo? = null,
        val passkeys: List<PasskeyInfo> = emptyList(),
        val sessions: List<SessionInfo> = emptyList(),
        val displayName: String = "",
        val email: String = "",
        val color: String = "",
        val dialog: Dialog? = null,
        /** suggest a passkey again, after "Later" on the step after sign-in */
        val showPasskeyReminder: Boolean = false,
        val loading: Boolean = false,
        val error: UiMessage? = null,
        val info: UiMessage? = null,
    )

    internal sealed interface UiEvent {
        data object Refresh : UiEvent
        data object DismissMessage : UiEvent
        data class DisplayNameChanged(val text: String) : UiEvent
        data class EmailChanged(val text: String) : UiEvent
        data class ColorChanged(val color: String) : UiEvent
        data object SaveProfileClicked : UiEvent
        data object ChangePasswordClicked : UiEvent
        data class ChangePasswordInput(val current: String, val new: String, val confirmation: String) : UiEvent {
            override fun toString() = "ChangePasswordInput"
        }

        data object ChangePasswordConfirmed : UiEvent
        data object EnableTotpClicked : UiEvent
        data class TotpCodeChanged(val text: String) : UiEvent
        data object ConfirmTotpClicked : UiEvent
        data object DisableTotpClicked : UiEvent
        data object DisableTotpConfirmed : UiEvent
        data object RegenerateRecoveryCodesClicked : UiEvent
        data object AddPasskeyClicked : UiEvent
        data class RenamePasskeyClicked(val passkey: PasskeyInfo) : UiEvent
        data class RenamePasskeyInput(val name: String) : UiEvent
        data object RenamePasskeyConfirmed : UiEvent
        data class DeletePasskeyClicked(val passkey: PasskeyInfo) : UiEvent
        data object DeletePasskeyConfirmed : UiEvent
        data class RevokeSessionClicked(val session: SessionInfo) : UiEvent
        data object RevokeOtherSessionsClicked : UiEvent
        data object SignOutClicked : UiEvent
        data object PasskeyReminderDismissed : UiEvent
        data class StepUpInput(val password: String, val code: String) : UiEvent {
            override fun toString() = "StepUpInput"
        }

        data object StepUpConfirmed : UiEvent
        data object StepUpWithPasskeyClicked : UiEvent

        /**
         * @param json the Credential Manager response, null when cancelled
         */
        data class PasskeyResult(val json: String?) : UiEvent
        data class PasskeyFailed(val error: Throwable) : UiEvent
        data object DismissDialog : UiEvent
    }

    internal sealed interface AccountEvent {
        data class CreatePasskey(val requestJson: String) : AccountEvent
        data class GetPasskey(val requestJson: String) : AccountEvent
        data class SavePassword(val username: String, val password: String) : AccountEvent {
            override fun toString() = "SavePassword(username=$username)"
        }

        /** after an explicit sign-out */
        data object SignedOut : AccountEvent

        /** the session is gone (expired or revoked elsewhere) */
        data class LoginRequired(val backendId: Long) : AccountEvent
    }

    companion object {
        /** docs/auth.md section 3; the server checks the rest */
        const val MIN_PASSWORD_LENGTH = 10
    }
}

