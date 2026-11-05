package de.markusressel.mkdocseditor.feature.join.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AccessRequestState
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.ServerFeature
import de.markusressel.mkdocseditor.auth.discovery.DiscoveredServer
import de.markusressel.mkdocseditor.auth.discovery.DiscoveryResult
import de.markusressel.mkdocseditor.auth.discovery.ServerDiscoverer
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.link.JoinLink
import de.markusressel.mkdocseditor.auth.link.JoinLinkParser
import de.markusressel.mkdocseditor.auth.token.AccessRequestStore
import de.markusressel.mkdocseditor.auth.token.PendingAccessRequest
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.ParsedBackendUrl
import de.markusressel.mkdocseditor.feature.auth.common.data.ServerSetup
import de.markusressel.mkdocseditor.feature.auth.common.data.UiMessage
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.baseUrl
import de.markusressel.mkdocseditor.feature.join.data.JoinTarget
import de.markusressel.mkdocseditor.feature.join.data.PendingJoin
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import de.markusressel.mkdocseditor.service.AccessRequestsWorker
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import java.io.IOException
import java.net.URI
import javax.inject.Inject

/**
 * Follows a join link: right into the document for someone who is signed in, else to signing in or to asking the admin
 * for an invite.
 */
@HiltViewModel
internal class JoinLinkViewModel @Inject constructor(
    private val discovery: ServerDiscoverer,
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
    private val requestStore: AccessRequestStore,
    private val pendingJoin: PendingJoin,
    private val navigation: MainNavigationRequests,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<JoinEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    private var linkText: String? = null
    private var link: JoinLink? = null
    private var server: DiscoveredServer? = null

    fun initialize(text: String) {
        if (linkText != null) return
        linkText = text
        link = JoinLinkParser.parse(text)
        launch { load() }
    }

    fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.RetryClicked -> launch { load() }
            is UiEvent.SignInClicked -> launch { signIn() }
            is UiEvent.RequestAccessClicked -> _uiState.update { it.copy(step = Step.Request, error = null) }
            is UiEvent.NameChanged -> _uiState.update { it.copy(name = event.text, error = null) }
            is UiEvent.MessageChanged -> _uiState.update { it.copy(message = event.text) }
            is UiEvent.SubmitClicked -> launch { submitRequest() }
            is UiEvent.BackClicked -> onBack()
            is UiEvent.WithdrawClicked -> launch {
                requestStore.clear()
                _uiState.update { it.copy(step = Step.Welcome, error = null) }
            }
        }
    }

    private fun onBack() {
        if (uiState.value.step == Step.Request) {
            _uiState.update { it.copy(step = Step.Welcome, error = null) }
        } else {
            launch { _events.send(JoinEvent.Close) }
        }
    }

    private suspend fun load() {
        val link = link ?: return showProblem(Problem.Invalid)
        _uiState.update { it.copy(step = Step.Loading, error = null) }
        val found = when (val result = discovery.discover(link.clientUrl)) {
            is DiscoveryResult.Found -> result.server
            is DiscoveryResult.Unreachable -> return showProblem(Problem.Unreachable)
            is DiscoveryResult.InvalidAddress, is DiscoveryResult.NotMkDocsEditor -> return showProblem(Problem.Invalid)
        }
        server = found
        _uiState.update {
            it.copy(
                serverName = found.displayName,
                serverHost = URI(found.backendUrl).host,
                canRequestAccess = ServerFeature.ACCESS_REQUESTS in found.info.features,
            )
        }

        val known = known(found)
        if (known != null && (found.info.authDisabled || authRepository.isSignedIn(known.id))) {
            if (requestStore.get()?.backendUrl == found.backendUrl) requestStore.clear()
            backends.select(known)
            pendingJoin.remember(JoinTarget(known.id, link.documentId, link.from))
            navigation.showFileBrowser()
            _events.send(JoinEvent.Close)
            return
        }

        if (requestStore.get()?.backendUrl == found.backendUrl) startWaiting() else _uiState.update { it.copy(step = Step.Welcome) }
    }

    private suspend fun known(found: DiscoveredServer): BackendConfig? =
        ParsedBackendUrl.parse(found.backendUrl)
            ?.let { BackendServerConfig(domain = it.host, port = it.port, useSsl = it.useSsl).baseUrl }
            ?.let { url -> backends.all().firstOrNull { it.serverConfig?.baseUrl == url } }

    /** adds the server only now: the user has seen its name and chosen to go on */
    private suspend fun connect(): BackendConfig? {
        val found = server ?: return null
        val link = link ?: return null
        val config = known(found) ?: backends.connect(
            ServerSetup(backendUrl = found.backendUrl, suggestedName = found.displayName, wikiUrl = found.wikiUrl)
        )
        pendingJoin.remember(JoinTarget(config.id, link.documentId, link.from))
        return config
    }

    private suspend fun signIn() {
        try {
            connect()?.let { _events.send(JoinEvent.OpenLogin(it.id)) }
        } catch (e: Exception) {
            Timber.w(e, "Adding the server failed")
            _uiState.update { it.copy(error = e.toAuthMessage()) }
        }
    }

    private suspend fun submitRequest() {
        val found = server ?: return
        val link = link ?: return
        val state = uiState.value
        val name = state.name.trim()
        if (name.isEmpty()) return
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            val created = authRepository.api(BackendRef(id = 0, baseUrl = found.backendUrl)).createAccessRequest(
                name = name,
                message = state.message.trim().takeIf { it.isNotEmpty() },
                documentId = link.documentId,
                invitedBy = link.from,
            )
            requestStore.put(
                PendingAccessRequest(
                    backendUrl = found.backendUrl,
                    token = created.token,
                    joinLink = linkText.orEmpty(),
                    serverName = found.displayName,
                )
            )
            startWaiting()
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.toAuthMessage()) }
        } finally {
            _uiState.update { it.copy(loading = false) }
        }
    }

    private fun startWaiting() = _uiState.update { it.copy(step = Step.Waiting, error = null) }

    /** Asks for the answer while the screen is visible. In the background the [AccessRequestsWorker] does, less often. */
    suspend fun pollRequest() {
        while (uiState.value.step == Step.Waiting) {
            checkRequest()
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun checkRequest() {
        val found = server ?: return
        val request = requestStore.get() ?: return
        try {
            val answer = authRepository.api(BackendRef(id = 0, baseUrl = found.backendUrl)).accessRequestStatus(request.token)
            val invite = answer.inviteToken
            when {
                answer.status == AccessRequestState.APPROVED && invite != null -> {
                    // stays stored until the account exists: the server keeps answering with the invite until then
                    connect()?.let {
                        _uiState.update { state -> state.copy(step = Step.Loading) }
                        _events.send(JoinEvent.OpenInvite(invite, it.id))
                    }
                }

                // the invite was used: the account exists, what is left is signing in
                answer.status == AccessRequestState.APPROVED -> {
                    requestStore.clear()
                    _uiState.update { it.copy(step = Step.Welcome) }
                }

                answer.status == AccessRequestState.REJECTED -> ended(Problem.Rejected)
                answer.status == AccessRequestState.EXPIRED -> ended(Problem.RequestExpired)
            }
        } catch (e: AuthApiException) {
            when (e.code) {
                AuthErrorCode.INVALID_ACCESS_REQUEST -> ended(Problem.RequestExpired)
                // the request stays stored: the admin can turn requests on again, and then it is still open
                AuthErrorCode.ACCESS_REQUESTS_DISABLED -> showProblem(Problem.RequestsDisabled)
                else -> Timber.w(e, "Checking the access request failed")
            }
        } catch (e: IOException) {
            // the admin takes a while anyway, the next check may get through
            Timber.w(e, "Checking the access request failed")
        }
    }

    private fun ended(problem: Problem) {
        requestStore.clear()
        showProblem(problem)
    }

    private fun showProblem(problem: Problem) = _uiState.update { it.copy(step = Step.Problem, problem = problem) }

    internal enum class Step { Loading, Welcome, Request, Waiting, Problem }

    internal enum class Problem(val message: Int, val canRetry: Boolean = false, val canRequestAgain: Boolean = false) {
        Invalid(R.string.join_problem_invalid),
        Unreachable(R.string.join_problem_unreachable, canRetry = true),
        Rejected(R.string.join_problem_rejected),
        RequestsDisabled(R.string.join_error_requests_disabled),
        RequestExpired(R.string.join_problem_request_expired, canRequestAgain = true),
    }

    internal data class UiState(
        val step: Step = Step.Loading,
        val problem: Problem? = null,
        val serverName: String = "",
        val serverHost: String = "",
        val canRequestAccess: Boolean = false,
        val name: String = "",
        val message: String = "",
        val loading: Boolean = false,
        val error: UiMessage? = null,
    )

    internal sealed interface UiEvent {
        data object RetryClicked : UiEvent
        data object SignInClicked : UiEvent
        data object RequestAccessClicked : UiEvent
        data class NameChanged(val text: String) : UiEvent
        data class MessageChanged(val text: String) : UiEvent
        data object SubmitClicked : UiEvent
        data object WithdrawClicked : UiEvent
        data object BackClicked : UiEvent
    }

    internal sealed interface JoinEvent {
        data class OpenLogin(val backendId: Long) : JoinEvent
        data class OpenInvite(val token: String, val backendId: Long) : JoinEvent {
            override fun toString() = "OpenInvite(backendId=$backendId, token=***)"
        }
        data object Close : JoinEvent
    }

    private companion object {
        /** the most docs/auth.md allows */
        const val POLL_INTERVAL_MS = 30_000L
    }
}
