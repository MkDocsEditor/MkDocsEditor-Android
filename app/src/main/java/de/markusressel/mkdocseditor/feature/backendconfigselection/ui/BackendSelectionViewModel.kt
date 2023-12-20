package de.markusressel.mkdocseditor.feature.backendconfigselection.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.feature.backendconfigselection.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfigselection.domain.GetBackendConfigItemsUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BackendSelectionViewModel @Inject constructor(
    private val getBackendConfigItemsUseCase: GetBackendConfigItemsUseCase,
) : ViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<BackendSelectionEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    init {
        reload()
    }

    fun onUiEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.BackendConfigClicked -> {
                    selectConfig(event.config)
                }

                is UiEvent.BackendConfigLongClicked -> {
                    editConfig(event.config)
                }

                is UiEvent.CreateBackendConfigClicked -> {
                    _events.send(BackendSelectionEvent.CreateBackend(ResourceEntity()))
                }

                is UiEvent.ExpandableFabItemSelected -> {
                    when (event.item.id) {
                        FAB_ID_CREATE_BACKEND_CONFIG -> {
                            navigateToCreateBackendConfig()
                        }
                    }
                }
            }
        }
    }

    private fun reload() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val items = getBackendConfigItemsUseCase()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    listItems = items
                )
            } catch (e: Exception) {
                showError(e.message ?: "Unknown error")
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun selectConfig(config: BackendConfig) {
        _uiState.update { old ->
            old.copy(
                selectedItem = config
            )
        }
    }

    private fun editConfig(config: BackendConfig) {
        // TODO: navigate to edit screen
    }


    private fun navigateToCreateBackendConfig() {
        // TODO: navigate to edit screen

    }

    private fun showError(s: String) {
        viewModelScope.launch {
            _events.send(BackendSelectionEvent.Error(s))
        }
    }
}

internal sealed class UiEvent {
    data class BackendConfigClicked(val config: BackendConfig) : UiEvent()
    data class BackendConfigLongClicked(val config: BackendConfig) : UiEvent()
    data object CreateBackendConfigClicked : UiEvent()

    data class ExpandableFabItemSelected(val item: FabConfig.Fab) : UiEvent()
}

internal data class UiState(
    val fabConfig: FabConfig = FabConfig(
        right = listOf(
            FabConfig.Fab(
                id = FAB_ID_CREATE_BACKEND_CONFIG,
                description = R.string.create_backend_config,
                icon = MaterialDesignIconic.Icon.gmi_file_add,
            ),
        )
    ),

    val isLoading: Boolean = false,
    val error: String? = null,
    val listItems: List<BackendConfig> = emptyList(),
    val selectedItem: BackendConfig? = null,
)

internal sealed class BackendSelectionEvent {
    data class Error(val message: String) : BackendSelectionEvent()

    data class SelectBackend(val entity: DocumentEntity) : BackendSelectionEvent()
    data class CreateBackend(val entity: ResourceEntity) : BackendSelectionEvent()
    data class EditBackend(val parentId: String) : BackendSelectionEvent()
}

const val FAB_ID_CREATE_BACKEND_CONFIG = 0