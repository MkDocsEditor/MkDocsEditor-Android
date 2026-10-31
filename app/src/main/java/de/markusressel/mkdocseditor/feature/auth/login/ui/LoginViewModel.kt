package de.markusressel.mkdocseditor.feature.auth.login.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import de.markusressel.mkdocseditor.auth.domain.SecondFactorMethod
import de.markusressel.mkdocseditor.auth.link.AuthLink
import de.markusressel.mkdocseditor.auth.link.AuthLinkParser
import de.markusressel.mkdocseditor.auth.link.ConnectLinkParser
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.CredentialClient
import de.markusressel.mkdocseditor.feature.auth.common.data.PickedCredential
import de.markusressel.mkdocseditor.feature.auth.common.data.UiMessage
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import de.markusressel.mkdocseditor.R
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backends: AuthBackends,
    internal val credentialClient: CredentialClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<LoginEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var backend: BackendRef? = null
    private var challenge: String? = null

    /** the password came from the credential provider, so there is nothing to offer for saving */
    private var passwordFromProvider = false

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
            _uiState.update { old ->
                old.copy(
                    backendName = config.name,
                    username = old.username.ifEmpty { backends.usernameHint(backendId).orEmpty() },
                )
            }
            loadStatus(ref)
        }
    }

    private suspend fun loadStatus(ref: BackendRef) {
        try {
            val status = authRepository.status(ref)
            _uiState.update { old ->
                old.copy(
                    passkeysEnabled = status.passkeys.enabled,
                    setupRequired = status.setupRequired,
                    authDisabled = status.authDisabled,
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not load the auth status")
            showError(e.toAuthMessage())
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.UsernameChanged -> _uiState.update { it.copy(username = event.text, error = null) }
                is UiEvent.PasswordChanged -> {
                    passwordFromProvider = false
                    _uiState.update { it.copy(password = event.text, error = null) }
                }

                is UiEvent.LoginClicked -> passwordLogin()
                is UiEvent.PasskeyClicked -> passkeyLogin()
                is UiEvent.SavedPasswordClicked -> _events.send(LoginEvent.PickCredential(null, includePasswords = true))
                is UiEvent.CredentialPicked -> onCredentialPicked(event.credential)
                is UiEvent.CredentialFailed -> {
                    pendingCeremonyId = null
                    _uiState.update { it.copy(loading = false) }
                    showError(event.error.toAuthMessage())
                }

                is UiEvent.CodeChanged -> onCodeChanged(event.text)
                is UiEvent.SubmitCodeClicked -> submitCode()
                is UiEvent.UseRecoveryCodeChanged -> _uiState.update {
                    it.copy(useRecoveryCode = event.enabled, code = "", error = null)
                }

                is UiEvent.BackToPasswordClicked -> backToCredentials(null)
                is UiEvent.LinkInputChanged -> _uiState.update { it.copy(linkInput = event.text, error = null) }
                is UiEvent.OpenLinkClicked -> openLink()
                is UiEvent.ContinueWithoutLoginClicked -> _events.send(LoginEvent.Close)
            }
        }
    }

    private suspend fun passwordLogin() {
        val ref = backend ?: return
        val state = uiState.value
        if (state.username.isBlank() || state.password.isEmpty()) {
            showError(UiMessage(R.string.auth_error_missing_credentials))
            return
        }
        runLoading {
            handleOutcome(ref, authRepository.login(ref, state.username, state.password), offerSave = !passwordFromProvider)
        }
    }

    private suspend fun passkeyLogin() {
        val ref = backend ?: return
        runLoading {
            val ceremony = authRepository.beginPasskeyLogin(ref, challenge)
            pendingCeremonyId = ceremony.ceremonyId
            // the provider sheet also lists saved passwords on the first step
            _events.send(LoginEvent.PickCredential(ceremony.publicKey, includePasswords = challenge == null))
        }
    }

    private var pendingCeremonyId: String? = null

    private suspend fun onCredentialPicked(credential: PickedCredential?) {
        val ref = backend ?: return
        val ceremonyId = pendingCeremonyId
        pendingCeremonyId = null
        when (credential) {
            null -> _uiState.update { it.copy(loading = false) }

            is PickedCredential.Password -> {
                passwordFromProvider = true
                _uiState.update { it.copy(username = credential.username, password = credential.password) }
                passwordLogin()
            }

            is PickedCredential.Passkey -> {
                if (ceremonyId == null) {
                    _uiState.update { it.copy(loading = false) }
                    return
                }
                runLoading {
                    handleOutcome(
                        ref,
                        authRepository.finishPasskeyLogin(ref, ceremonyId, credential.authenticationResponseJson),
                        offerSave = false,
                    )
                }
            }
        }
    }

    private suspend fun onCodeChanged(text: String) {
        val recovery = uiState.value.useRecoveryCode
        // authenticator codes may be pasted with spaces ("123 456")
        val code = if (recovery) text.take(32) else text.filter(Char::isDigit).take(TOTP_LENGTH)
        _uiState.update { it.copy(code = code, error = null) }
        if (!recovery && code.length == TOTP_LENGTH && !uiState.value.loading) {
            submitCode()
        }
    }

    private suspend fun submitCode() {
        val ref = backend ?: return
        val currentChallenge = challenge ?: return
        val state = uiState.value
        if (!state.useRecoveryCode && state.code.length != TOTP_LENGTH) {
            showError(UiMessage(R.string.auth_error_code_length))
            return
        }
        if (state.useRecoveryCode && state.code.isBlank()) return
        runLoading {
            val outcome = when (state.useRecoveryCode) {
                true -> authRepository.submitRecoveryCode(ref, currentChallenge, state.code)
                false -> authRepository.submitTotpCode(ref, currentChallenge, state.code)
            }
            handleOutcome(ref, outcome, offerSave = !passwordFromProvider)
        }
    }

    private suspend fun handleOutcome(ref: BackendRef, outcome: LoginOutcome, offerSave: Boolean) {
        when (outcome) {
            is LoginOutcome.SecondFactorRequired -> {
                challenge = outcome.challenge
                _uiState.update {
                    it.copy(
                        step = Step.SecondFactor,
                        methods = outcome.methods,
                        useRecoveryCode = SecondFactorMethod.Totp !in outcome.methods &&
                            SecondFactorMethod.RecoveryCode in outcome.methods,
                        code = "",
                    )
                }
            }

            is LoginOutcome.SignedIn -> {
                challenge = null
                val state = uiState.value
                val username = outcome.user?.username ?: state.username
                if (username.isNotBlank()) backends.setUsernameHint(ref.id, username)
                if (offerSave && state.password.isNotEmpty() && state.username.isNotBlank()) {
                    _events.send(LoginEvent.SavePassword(state.username.trim(), state.password))
                }
                _uiState.update { it.copy(password = "", code = "") }
                _events.send(LoginEvent.SignedIn(ref.id, outcome.secondFactorSetupRequired))
            }
        }
    }

    private fun backToCredentials(error: UiMessage?) {
        challenge = null
        _uiState.update { it.copy(step = Step.Credentials, code = "", error = error) }
    }

    private suspend fun openLink() {
        val ref = backend ?: return
        val input = uiState.value.linkInput.trim()
        if (ConnectLinkParser.isConnectLink(input)) {
            _events.send(LoginEvent.OpenConnectLink(input))
            return
        }
        val link = AuthLinkParser.parse(input)
        if (link == null) {
            showError(UiMessage(R.string.auth_error_link_unreadable))
            return
        }
        _events.send(LoginEvent.OpenLink(link, ref.id))
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            block()
        } catch (e: AuthApiException) {
            Timber.w(e, "Login failed")
            if (e.code == AuthErrorCode.CHALLENGE_EXPIRED && challenge != null) {
                backToCredentials(e.toAuthMessage())
            } else {
                showError(e.toAuthMessage())
            }
        } catch (e: Exception) {
            Timber.w(e, "Login failed")
            showError(e.toAuthMessage())
        } finally {
            if (pendingCeremonyId == null) {
                _uiState.update { it.copy(loading = false) }
            }
        }
    }

    private fun showError(message: UiMessage) {
        _uiState.update { it.copy(error = message) }
    }

    enum class Step { Credentials, SecondFactor }

    internal data class UiState(
        val backendName: String = "",
        val step: Step = Step.Credentials,
        val username: String = "",
        val password: String = "",
        val passkeysEnabled: Boolean = false,
        val setupRequired: Boolean = false,
        val authDisabled: Boolean = false,
        val methods: Set<SecondFactorMethod> = emptySet(),
        val useRecoveryCode: Boolean = false,
        val code: String = "",
        val linkInput: String = "",
        val loading: Boolean = false,
        val error: UiMessage? = null,
    ) {
        override fun toString() = "UiState(step=$step, username=$username, loading=$loading, error=$error)"
    }

    internal sealed interface UiEvent {
        data class UsernameChanged(val text: String) : UiEvent
        data class PasswordChanged(val text: String) : UiEvent
        data object LoginClicked : UiEvent
        data object PasskeyClicked : UiEvent
        data object SavedPasswordClicked : UiEvent
        data class CredentialPicked(val credential: PickedCredential?) : UiEvent
        data class CredentialFailed(val error: Throwable) : UiEvent
        data class CodeChanged(val text: String) : UiEvent
        data object SubmitCodeClicked : UiEvent
        data class UseRecoveryCodeChanged(val enabled: Boolean) : UiEvent
        data object BackToPasswordClicked : UiEvent
        data class LinkInputChanged(val text: String) : UiEvent
        data object OpenLinkClicked : UiEvent
        data object ContinueWithoutLoginClicked : UiEvent
    }

    internal sealed interface LoginEvent {
        /**
         * Show the Credential Manager sheet
         */
        data class PickCredential(val passkeyRequestJson: String?, val includePasswords: Boolean) : LoginEvent

        /**
         * Offer to save the typed password in the credential provider
         */
        data class SavePassword(val username: String, val password: String) : LoginEvent {
            override fun toString() = "SavePassword(username=$username)"
        }

        data class SignedIn(val backendId: Long, val secondFactorSetupRequired: Boolean) : LoginEvent
        data class OpenLink(val link: AuthLink, val backendId: Long) : LoginEvent
        data class OpenConnectLink(val link: String) : LoginEvent {
            override fun toString() = "OpenConnectLink"
        }
        data object Close : LoginEvent
    }

    companion object {
        const val TOTP_LENGTH = 6
    }
}
