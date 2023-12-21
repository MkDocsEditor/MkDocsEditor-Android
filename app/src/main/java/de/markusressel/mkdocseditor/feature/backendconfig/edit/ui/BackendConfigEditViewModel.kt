package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendAuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendAuthConfigsUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.ValidateBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.SaveBackendConfigItemsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class BackendConfigEditViewModel @Inject constructor(
    private val getBackendAuthConfigsUseCase: GetBackendAuthConfigsUseCase,
    private val validateBackendConfigUseCase: ValidateBackendConfigUseCase,
    private val saveBackendConfigItemsUseCase: SaveBackendConfigItemsUseCase,
) : ViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<BackendEditEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            updateAuthConfigs()
        }
    }

    private suspend fun updateAuthConfigs() {
        val authConfigs = getBackendAuthConfigsUseCase()
        _uiState.value = _uiState.value.copy(
            authConfigs = authConfigs
        )
    }

    fun onUiEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.AuthConfigChanged -> {
                    _uiState.value = _uiState.value.copy(
                        authConfig = event.authConfig
                    )
                }

                is UiEvent.SaveClicked -> {
                    // TODO
//                    val isValid = validateBackendConfigUseCase(config)
                    val isValid = false
                    if (!isValid) {
                        return@launch
                    } else {
                        val config = BackendConfig(
                            name = uiState.value.name,
                            description = uiState.value.description,
                            serverConfig = requireNotNull(uiState.value.serverConfig),
                            authConfig = requireNotNull(uiState.value.authConfig),
                        )
                        saveBackendConfigItemsUseCase(config)
                    }
                }
            }
        }
    }

    private fun showError(s: String) {
        viewModelScope.launch {
            _events.send(BackendEditEvent.Error(s))
        }
    }


    internal sealed class UiEvent {
        data object SaveClicked : UiEvent()
        data class AuthConfigChanged(val authConfig: BackendAuthConfig?) : UiEvent()
    }

    internal data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,

        val authConfigs: List<BackendAuthConfig> = emptyList(),

        val name: String = "",
        val description: String = "",
        val serverConfig: BackendServerConfig? = null,
        val authConfig: BackendAuthConfig? = null,
    )

    internal sealed class BackendEditEvent {
        data class Error(val message: String) : BackendEditEvent()
    }
}
