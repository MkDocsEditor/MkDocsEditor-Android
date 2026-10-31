package de.markusressel.mkdocseditor.feature.auth.onboarding.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import de.markusressel.mkdocseditor.auth.link.ConnectLinkParser
import de.markusressel.mkdocseditor.auth.link.ConnectLinkResult
import de.markusressel.mkdocseditor.auth.link.ConnectPayload
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.ServerSetup
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
import java.io.IOException
import java.net.URI
import java.time.Clock
import javax.inject.Inject

/**
 * A connect link from the web client (device pairing): shows the server and the account,
 * then signs in with the link's single-use token. No address, username or password typed.
 */
@HiltViewModel
internal class ConnectLinkViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val backends: AuthBackends,
) : ViewModel() {

    /** for tests */
    internal var clock: Clock = Clock.systemUTC()

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<ConnectLinkEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var linkText: String? = null
    private var payload: ConnectPayload? = null
    private var serverInfo: ServerInfo? = null

    fun initialize(link: String) {
        if (linkText == link) return
        linkText = link
        launch { load() }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.ConnectClicked -> connect()
                is UiEvent.RetryClicked -> load()
                is UiEvent.SignInManuallyClicked -> signInManually()
                is UiEvent.DoneClicked -> _events.send(ConnectLinkEvent.Close)
            }
        }
    }

    private suspend fun load() {
        val result = linkText?.let(ConnectLinkParser::parse)
        val parsed = when (result) {
            is ConnectLinkResult.Valid -> result.payload
            is ConnectLinkResult.UnsupportedVersion -> return showProblem(Problem.UpdateApp)
            ConnectLinkResult.Invalid, null -> return showProblem(Problem.Invalid)
        }
        payload = parsed
        _uiState.update {
            it.copy(step = Step.Loading, username = parsed.username, serverName = URI(parsed.backendUrl).host, error = null)
        }

        serverInfo = try {
            authRepository.serverInfo(BackendRef(id = 0, baseUrl = parsed.backendUrl))
        } catch (e: AuthApiException) {
            // a backend without /server-info/ still pairs; the host is the name
            null
        } catch (e: IOException) {
            Timber.w(e, "Server info failed")
            return showProblem(Problem.Unreachable)
        }
        serverInfo?.name?.takeIf { it.isNotBlank() }?.let { name -> _uiState.update { it.copy(serverName = name) } }

        if (parsed.isExpired(clock.instant())) return showProblem(Problem.Expired)
        _uiState.update { it.copy(step = Step.Confirm) }
    }

    private suspend fun connect() {
        val parsed = payload ?: return
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            val config = backendConfig(parsed)
            val ref = requireNotNull(config.toBackendRef())
            when (val outcome = authRepository.redeemDeviceLink(ref, parsed)) {
                is LoginOutcome.SignedIn -> {
                    backends.setUsernameHint(config.id, outcome.user?.username ?: parsed.username)
                    backends.select(config)
                    _uiState.update { it.copy(step = Step.Done, backendId = config.id) }
                    _events.send(ConnectLinkEvent.Connected(config.id))
                }

                // the device link stands for the full login, the server never asks for more
                is LoginOutcome.SecondFactorRequired -> showProblem(Problem.Invalid)
            }
        } catch (e: AuthApiException) {
            when (e.code) {
                AuthErrorCode.INVALID_LINK -> showProblem(Problem.Expired)
                AuthErrorCode.ACCOUNT_DISABLED -> showProblem(Problem.AccountDisabled)
                else -> _uiState.update { it.copy(error = e.toAuthMessage()) }
            }
        } catch (e: Exception) {
            Timber.w(e, "Connecting failed")
            _uiState.update { it.copy(error = e.toAuthMessage()) }
        } finally {
            _uiState.update { it.copy(loading = false) }
        }
    }

    private suspend fun signInManually() {
        val parsed = payload ?: return
        val config = backendConfig(parsed)
        backends.setUsernameHint(config.id, parsed.username)
        _events.send(ConnectLinkEvent.OpenLogin(config.id))
    }

    /** the config of the link's backend: an existing one with that URL, else a new one */
    private suspend fun backendConfig(parsed: ConnectPayload): BackendConfig = backends.connect(
        ServerSetup(
            backendUrl = parsed.backendUrl,
            suggestedName = serverInfo?.name,
            wikiUrl = serverInfo?.wikiUrl,
        )
    )

    private fun showProblem(problem: Problem) {
        _uiState.update { it.copy(step = Step.Problem, problem = problem, loading = false) }
    }

    enum class Step { Loading, Confirm, Problem, Done }

    enum class Problem(val message: Int, val canSignInManually: Boolean, val canRetry: Boolean = false) {
        Invalid(R.string.connect_error_invalid, canSignInManually = false),
        UpdateApp(R.string.connect_error_update_app, canSignInManually = false),
        Expired(R.string.connect_error_expired, canSignInManually = true),
        AccountDisabled(R.string.auth_error_account_disabled, canSignInManually = false),
        Unreachable(R.string.connect_error_unreachable, canSignInManually = true, canRetry = true),
    }

    internal data class UiState(
        val step: Step = Step.Loading,
        val serverName: String = "",
        val username: String = "",
        val problem: Problem? = null,
        val backendId: Long? = null,
        val loading: Boolean = false,
        val error: UiMessage? = null,
    )

    internal sealed interface UiEvent {
        data object ConnectClicked : UiEvent
        data object RetryClicked : UiEvent
        data object SignInManuallyClicked : UiEvent
        data object DoneClicked : UiEvent
    }

    internal sealed interface ConnectLinkEvent {
        data class Connected(val backendId: Long) : ConnectLinkEvent
        data class OpenLogin(val backendId: Long) : ConnectLinkEvent
        data object Close : ConnectLinkEvent
    }
}
