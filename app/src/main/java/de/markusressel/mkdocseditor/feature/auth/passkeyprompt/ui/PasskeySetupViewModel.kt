package de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
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
 * "Sign in with a passkey next time", right after the first sign-in on this device. The new
 * session may start the registration without another password prompt (docs/auth.md).
 */
@HiltViewModel
internal class PasskeySetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backends: AuthBackends,
    private val store: PasskeyPromptStore,
    internal val providers: CredentialProviders,
    internal val credentialClient: CredentialClient,
) : ViewModel() {

    /** for tests */
    internal var clock: Clock = Clock.systemUTC()

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<PasskeySetupEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var backend: BackendRef? = null
    private var pendingCeremonyId: String? = null

    fun initialize(backendId: Long) {
        if (backend?.id == backendId) return
        launch {
            val ref = backends.get(backendId)?.toBackendRef()
            if (ref == null) {
                _events.send(PasskeySetupEvent.Finish)
                return@launch
            }
            backend = ref
            val offer = try {
                val passkeysEnabled = authRepository.status(ref).passkeys.enabled
                val passkeys = if (passkeysEnabled) authRepository.authenticated(ref) { passkeys(it) } else emptyList()
                PasskeyPromptPolicy.shouldOfferAfterSignIn(
                    passkeysEnabled = passkeysEnabled,
                    passkeys = passkeys,
                    state = store.state(backendId),
                    isInstalled = providers::isInstalled,
                )
            } catch (e: Exception) {
                // never block the way into the app over a suggestion
                Timber.w(e, "Passkey suggestion skipped")
                false
            }
            if (!offer) {
                _events.send(PasskeySetupEvent.Finish)
                return@launch
            }
            _uiState.update {
                it.copy(
                    step = Step.Offer,
                    bitwardenInstalled = providers.isInstalled(PasskeyPromptPolicy.BITWARDEN_PACKAGE),
                    canOpenProviderSettings = providers.canOpenProviderSettings(),
                )
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.CreateClicked -> begin()
                is UiEvent.PasskeyCreated -> finish(event.registrationResponseJson)
                is UiEvent.PasskeyFailed -> {
                    pendingCeremonyId = null
                    _uiState.update { it.copy(loading = false, error = event.error.toAuthMessage()) }
                }

                is UiEvent.LaterClicked -> {
                    backend?.let { store.skipped(it.id, clock.instant()) }
                    _events.send(PasskeySetupEvent.Finish)
                }

                is UiEvent.DoneClicked -> _events.send(PasskeySetupEvent.Finish)
            }
        }
    }

    private suspend fun begin() {
        val ref = backend ?: return
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            val ceremony = authRepository.authenticated(ref) { beginAddPasskey(it) }
            pendingCeremonyId = ceremony.ceremonyId
            _events.send(PasskeySetupEvent.CreatePasskey(ceremony.publicKey))
        } catch (e: AuthApiException) {
            val message = if (e.code == AuthErrorCode.REAUTH_REQUIRED) {
                // a backend from before the fresh-session rule: the account screen asks for the step-up
                UiMessage(R.string.passkey_setup_error_reauth)
            } else e.toAuthMessage()
            _uiState.update { it.copy(loading = false, error = message) }
        } catch (e: Exception) {
            _uiState.update { it.copy(loading = false, error = e.toAuthMessage()) }
        }
    }

    /**
     * @param json the Credential Manager response, null when the user cancelled
     */
    private suspend fun finish(json: String?) {
        val ref = backend ?: return
        val ceremonyId = pendingCeremonyId
        pendingCeremonyId = null
        if (json == null || ceremonyId == null) {
            _uiState.update { it.copy(loading = false) }
            return
        }
        try {
            val passkey = authRepository.authenticated(ref) { finishAddPasskey(it, ceremonyId, json, name = null) }
            store.registered(ref.id, passkey.id)
            _uiState.update { it.copy(step = Step.Done, provider = passkey.provider, loading = false) }
        } catch (e: Exception) {
            Timber.w(e, "Passkey registration failed")
            _uiState.update { it.copy(loading = false, error = e.toAuthMessage()) }
        }
    }

    enum class Step { Loading, Offer, Done }

    internal data class UiState(
        val step: Step = Step.Loading,
        val bitwardenInstalled: Boolean = false,
        val canOpenProviderSettings: Boolean = false,
        val provider: String? = null,
        val loading: Boolean = false,
        val error: UiMessage? = null,
    )

    internal sealed interface UiEvent {
        data object CreateClicked : UiEvent

        /** @param registrationResponseJson null when cancelled */
        data class PasskeyCreated(val registrationResponseJson: String?) : UiEvent
        data class PasskeyFailed(val error: Throwable) : UiEvent
        data object LaterClicked : UiEvent
        data object DoneClicked : UiEvent
    }

    internal sealed interface PasskeySetupEvent {
        data class CreatePasskey(val requestJson: String) : PasskeySetupEvent

        /** go on into the app */
        data object Finish : PasskeySetupEvent
    }
}
