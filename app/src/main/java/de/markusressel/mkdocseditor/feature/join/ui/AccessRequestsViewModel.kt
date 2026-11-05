package de.markusressel.mkdocseditor.feature.join.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.auth.api.AccessRequestInfo
import de.markusressel.mkdocseditor.auth.api.AuthApi
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.UiMessage
import de.markusressel.mkdocseditor.feature.auth.common.data.toAuthMessage
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/** The admin's list of people who asked for access */
@HiltViewModel
internal class AccessRequestsViewModel @Inject constructor(
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private var backend: BackendRef? = null

    fun initialize(backendId: Long) {
        if (backend != null) return
        launch {
            backend = backends.get(backendId)?.toBackendRef()
            load()
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.ApproveClicked -> _uiState.update { it.copy(approving = event.request, error = null) }
                is UiEvent.DialogDismissed -> _uiState.update { it.copy(approving = null) }
                is UiEvent.RoleConfirmed -> run(event.request) { token ->
                    approveAccessRequest(token, event.request.id, event.role)
                }
                is UiEvent.RejectClicked -> run(event.request) { token -> rejectAccessRequest(token, event.request.id) }
            }
        }
    }

    private suspend fun load() {
        val backend = backend ?: return
        _uiState.update { it.copy(loading = true, error = null) }
        try {
            val requests = authRepository.authenticated(backend) { pendingAccessRequests(it) }
            _uiState.update { it.copy(requests = requests) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.toAuthMessage()) }
        } finally {
            _uiState.update { it.copy(loading = false) }
        }
    }

    private suspend fun run(request: AccessRequestInfo, call: suspend AuthApi.(String) -> Unit) {
        val backend = backend ?: return
        _uiState.update { it.copy(loading = true, approving = null, error = null) }
        try {
            authRepository.authenticated(backend) { call(it) }
            _uiState.update { state -> state.copy(requests = state.requests.filterNot { it.id == request.id }) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.toAuthMessage()) }
        } finally {
            _uiState.update { it.copy(loading = false) }
        }
    }

    internal data class UiState(
        val requests: List<AccessRequestInfo> = emptyList(),
        val approving: AccessRequestInfo? = null,
        val loading: Boolean = false,
        val error: UiMessage? = null,
    )

    internal sealed interface UiEvent {
        data class ApproveClicked(val request: AccessRequestInfo) : UiEvent
        data class RoleConfirmed(val request: AccessRequestInfo, val role: String) : UiEvent
        data class RejectClicked(val request: AccessRequestInfo) : UiEvent
        data object DialogDismissed : UiEvent
    }
}
