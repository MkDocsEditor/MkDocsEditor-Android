package de.markusressel.mkdocseditor.feature.auth.link.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.LinkInfo
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import de.markusressel.mkdocseditor.auth.link.AuthLink
import de.markusressel.mkdocseditor.auth.link.AuthLinkParser
import de.markusressel.mkdocseditor.auth.link.LinkKind
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.CredentialClient
import de.markusressel.mkdocseditor.feature.auth.common.data.ParsedBackendUrl
import de.markusressel.mkdocseditor.feature.auth.common.data.UiMessage
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

/**
 * Setup, invite and reset links: create the first admin, create an invited account, or set a
 * new password (docs/auth.md sections 7 and 8)
 */
@HiltViewModel
internal class AuthLinkViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backends: AuthBackends,
    internal val credentialClient: CredentialClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<LinkEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var link: AuthLink? = null
    private var backendConfig: BackendConfig? = null
    private var pendingCeremonyId: String? = null

    fun initialize(linkText: String, backendId: Long?) {
        if (link != null) return
        launch {
            val parsed = AuthLinkParser.parse(linkText)
            if (parsed == null) {
                _uiState.update { it.copy(step = Step.Invalid, error = UiMessage(R.string.auth_error_link_unreadable)) }
                return@launch
            }
            link = parsed
            val all = backends.all()
            val preselected = backendId?.let { id -> all.firstOrNull { it.id == id } }
                ?: backends.selected()
                ?: all.singleOrNull()
            _uiState.update {
                it.copy(
                    kindHint = parsed.kind,
                    backends = all.map { config -> BackendItem(config.id, config.name) },
                    selectedBackendId = preselected?.id,
                    step = Step.ChooseBackend,
                )
            }
            // a link opened from the app (pasted on the login screen) already knows its backend
            if (backendId != null && preselected != null) {
                useBackend(preselected)
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.BackendSelected -> _uiState.update { it.copy(selectedBackendId = event.id, error = null) }
                is UiEvent.BackendUrlChanged -> _uiState.update {
                    it.copy(backendUrl = event.text, selectedBackendId = null, error = null)
                }

                is UiEvent.ContinueClicked -> continueWithBackend()
                is UiEvent.UsernameChanged -> _uiState.update { it.copy(username = event.text, error = null) }
                is UiEvent.DisplayNameChanged -> _uiState.update { it.copy(displayName = event.text, error = null) }
                is UiEvent.PasswordChanged -> _uiState.update { it.copy(password = event.text, error = null) }
                is UiEvent.PasswordConfirmationChanged -> _uiState.update {
                    it.copy(passwordConfirmation = event.text, error = null)
                }

                is UiEvent.SubmitPasswordClicked -> submitPassword()
                is UiEvent.CreatePasskeyClicked -> beginPasskey()
                is UiEvent.PasskeyCreated -> finishPasskey(event.registrationResponseJson)
                is UiEvent.PasskeyFailed -> {
                    pendingCeremonyId = null
                    _uiState.update { it.copy(loading = false, error = event.error.toAuthMessage()) }
                }

                is UiEvent.DoneClicked -> _events.send(LinkEvent.Close)
            }
        }
    }

    private suspend fun continueWithBackend() {
        val state = uiState.value
        val selected = state.selectedBackendId?.let { backends.get(it) }
        if (selected != null) {
            useBackend(selected)
            return
        }
        val url = ParsedBackendUrl.parse(state.backendUrl)
        if (url == null) {
            showError(UiMessage(R.string.auth_error_backend_url))
            return
        }
        runLoading {
            useBackend(backends.create(url))
        }
    }

    private suspend fun useBackend(config: BackendConfig) {
        val ref = config.toBackendRef() ?: return
        val currentLink = link ?: return
        backendConfig = config
        runLoading {
            val status = authRepository.status(ref)
            val info = authRepository.inspectLink(ref, currentLink)
            showForm(info, status.passkeys.enabled, status.passwordPolicy.minLength, config.name)
        }
    }

    private fun showForm(info: LinkInfo, passkeysEnabled: Boolean, minPasswordLength: Int, backendName: String) {
        val kind = LinkKind.fromApi(info.kind) ?: uiState.value.kindHint
        _uiState.update {
            it.copy(
                step = Step.Form,
                kind = kind,
                backendName = backendName,
                role = info.role,
                note = info.note,
                resetUsername = info.username,
                resetSecondFactors = info.resetSecondFactors,
                expiresAt = info.expiresAt?.toString(),
                passkeysEnabled = passkeysEnabled,
                minPasswordLength = minPasswordLength,
            )
        }
    }

    private val isReset: Boolean
        get() = uiState.value.kind == LinkKind.Reset

    /**
     * @return a message if the inputs cannot be sent, null when fine
     */
    private fun validate(withPassword: Boolean): UiMessage? {
        val state = uiState.value
        if (!isReset && !USERNAME_PATTERN.matches(state.username.trim())) {
            return UiMessage(R.string.auth_error_invalid_username)
        }
        if (withPassword) {
            if (state.password.codePointCount(0, state.password.length) < state.minPasswordLength) {
                return UiMessage(R.string.auth_error_password_too_short, listOf(state.minPasswordLength))
            }
            if (state.password != state.passwordConfirmation) {
                return UiMessage(R.string.auth_error_password_mismatch)
            }
        }
        return null
    }

    private suspend fun submitPassword() {
        val ref = backendConfig?.toBackendRef() ?: return
        val currentLink = link ?: return
        validate(withPassword = true)?.let {
            showError(it)
            return
        }
        val state = uiState.value
        runLoading {
            val outcome = authRepository.redeemLinkWithPassword(
                ref,
                currentLink,
                username = if (isReset) null else state.username,
                displayName = if (isReset) null else state.displayName,
                password = state.password,
            )
            val username = (outcome as? LoginOutcome.SignedIn)?.user?.username
                ?: state.resetUsername ?: state.username.trim()
            _events.send(LinkEvent.SavePassword(username, state.password))
            onSignedIn(ref, outcome)
        }
    }

    private suspend fun beginPasskey() {
        val ref = backendConfig?.toBackendRef() ?: return
        val currentLink = link ?: return
        validate(withPassword = false)?.let {
            showError(it)
            return
        }
        val state = uiState.value
        runLoading {
            val ceremony = authRepository.beginLinkPasskey(
                ref,
                currentLink,
                username = if (isReset) null else state.username,
                displayName = if (isReset) null else state.displayName,
            )
            pendingCeremonyId = ceremony.ceremonyId
            _events.send(LinkEvent.CreatePasskey(ceremony.publicKey))
        }
    }

    private suspend fun finishPasskey(registrationResponseJson: String?) {
        val ref = backendConfig?.toBackendRef() ?: return
        val ceremonyId = pendingCeremonyId
        pendingCeremonyId = null
        if (registrationResponseJson == null || ceremonyId == null) {
            _uiState.update { it.copy(loading = false) }
            return
        }
        runLoading {
            val outcome = authRepository.finishLinkPasskey(ref, ceremonyId, registrationResponseJson, passkeyName = null)
            onSignedIn(ref, outcome)
        }
    }

    private suspend fun onSignedIn(ref: BackendRef, outcome: LoginOutcome) {
        val config = backendConfig ?: return
        val signedIn = outcome as? LoginOutcome.SignedIn ?: return
        signedIn.user?.username?.let { backends.setUsernameHint(ref.id, it) }
        backends.select(config)
        _uiState.update {
            it.copy(
                step = Step.Done,
                password = "",
                passwordConfirmation = "",
                signedInUsername = signedIn.user?.username,
                secondFactorSetupRequired = signedIn.secondFactorSetupRequired,
            )
        }
    }

    private suspend fun runLoading(block: suspend () -> Unit) {
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            block()
        } catch (e: Exception) {
            Timber.w(e, "Link flow failed")
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

    enum class Step { Loading, Invalid, ChooseBackend, Form, Done }

    internal data class BackendItem(val id: Long, val name: String)

    internal data class UiState(
        val step: Step = Step.Loading,
        val kindHint: LinkKind? = null,
        val kind: LinkKind? = null,
        val backends: List<BackendItem> = emptyList(),
        val selectedBackendId: Long? = null,
        val backendUrl: String = "",
        val backendName: String = "",
        val role: String? = null,
        val note: String? = null,
        val resetUsername: String? = null,
        val resetSecondFactors: Boolean = false,
        val expiresAt: String? = null,
        val passkeysEnabled: Boolean = false,
        val minPasswordLength: Int = 10,
        val username: String = "",
        val displayName: String = "",
        val password: String = "",
        val passwordConfirmation: String = "",
        val signedInUsername: String? = null,
        val secondFactorSetupRequired: Boolean = false,
        val loading: Boolean = false,
        val error: UiMessage? = null,
    ) {
        override fun toString() = "UiState(step=$step, kind=$kind, username=$username, loading=$loading, error=$error)"
    }

    internal sealed interface UiEvent {
        data class BackendSelected(val id: Long) : UiEvent
        data class BackendUrlChanged(val text: String) : UiEvent
        data object ContinueClicked : UiEvent
        data class UsernameChanged(val text: String) : UiEvent
        data class DisplayNameChanged(val text: String) : UiEvent
        data class PasswordChanged(val text: String) : UiEvent
        data class PasswordConfirmationChanged(val text: String) : UiEvent
        data object SubmitPasswordClicked : UiEvent
        data object CreatePasskeyClicked : UiEvent

        /**
         * @param registrationResponseJson null when the user cancelled
         */
        data class PasskeyCreated(val registrationResponseJson: String?) : UiEvent
        data class PasskeyFailed(val error: Throwable) : UiEvent
        data object DoneClicked : UiEvent
    }

    internal sealed interface LinkEvent {
        data class CreatePasskey(val requestJson: String) : LinkEvent
        data class SavePassword(val username: String, val password: String) : LinkEvent {
            override fun toString() = "SavePassword(username=$username)"
        }

        data object Close : LinkEvent
    }

    companion object {
        /** docs/auth.md section 2 */
        val USERNAME_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,31}$")
    }
}
