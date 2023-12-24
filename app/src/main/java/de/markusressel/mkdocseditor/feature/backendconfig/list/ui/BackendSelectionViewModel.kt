package de.markusressel.mkdocseditor.feature.backendconfig.list.ui

import androidx.lifecycle.ViewModel
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendConfigItemsUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
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
        launch {
            when (event) {
                is UiEvent.BackendConfigClicked -> {
                    selectConfig(event.config)
                }

                is UiEvent.BackendConfigLongClicked -> {
                    editConfig(event.config)
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
        launch {
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

    private suspend fun editConfig(config: BackendConfig) {
        _events.send(BackendSelectionEvent.EditBackend(config.id))
    }


    private suspend fun navigateToCreateBackendConfig() {
        _events.send(BackendSelectionEvent.CreateBackend(ResourceEntity()))
    }

    private suspend fun showError(s: String) {
        _events.send(BackendSelectionEvent.Error(s))
    }


    internal sealed class UiEvent {
        data class BackendConfigClicked(val config: BackendConfig) : UiEvent()
        data class BackendConfigLongClicked(val config: BackendConfig) : UiEvent()
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

        data class CreateBackend(val entity: ResourceEntity) : BackendSelectionEvent()
        data class EditBackend(val id: Long) : BackendSelectionEvent()
    }
}


const val FAB_ID_CREATE_BACKEND_CONFIG = 0