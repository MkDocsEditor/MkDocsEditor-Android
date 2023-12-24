package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendAuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendAuthConfigsUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.ValidateBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.SaveBackendConfigItemsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject


@HiltViewModel
internal class BackendConfigEditViewModel @Inject constructor(
    private val getBackendConfigUseCase: GetBackendConfigUseCase,
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
        launch {
            updateAuthConfigs()
        }
    }

    fun initialize(id: Long?) {
        launch {
            if (id == null) {
                clearInputs()
            } else {
                loadBackendConfig(id)
            }
        }
    }

    private fun clearInputs() {
        _uiState.value = _uiState.value.copy(
            name = "",
            description = "",
            serverConfig = null,
            authConfig = null,
        )
    }

    private suspend fun loadBackendConfig(id: Long) {
        val data = getBackendConfigUseCase(id)
        if (data == null) {
            showError("Backend config not found")
            return
        }

        _uiState.value = _uiState.value.copy(
            name = data.name,
            description = data.description,
            serverConfig = data.serverConfig,
            authConfig = data.authConfig,
        )
    }

    private suspend fun updateAuthConfigs() {
        val authConfigs = getBackendAuthConfigsUseCase()
        _uiState.value = _uiState.value.copy(
            authConfigs = authConfigs
        )
    }

    fun onUiEvent(event: UiEvent) {
        launch {
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

                is UiEvent.NameChanged -> processNameInput(event.text)
            }
        }
    }

    private fun processNameInput(text: String) {
        _uiState.value = _uiState.value.copy(
            name = text
        )
    }

    private fun showError(s: String) {
        launch {
            _events.send(BackendEditEvent.Error(s))
        }
    }


    internal sealed class UiEvent {
        data object SaveClicked : UiEvent()
        data class AuthConfigChanged(val authConfig: BackendAuthConfig?) : UiEvent()

        data class NameChanged(val text: String) : UiEvent()
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
