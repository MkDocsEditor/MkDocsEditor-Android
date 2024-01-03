package de.markusressel.mkdocseditor.feature.backendconfig.list.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetBackendConfigsFlowUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.list.domain.SelectBackendConfigUseCase
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class BackendSelectionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getBackendConfigsFlowUseCase: GetBackendConfigsFlowUseCase,
    private val selectBackendConfigUseCase: SelectBackendConfigUseCase,
) : ViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<BackendSelectionEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    init {
        launch {
            getBackendConfigsFlowUseCase().collectLatest {
                _uiState.update { old ->
                    old.copy(
                        isLoading = false,
                        listItems = it
                    )
                }
            }
        }
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

//    private fun reload() {
//        launch {
//            try {
//                _uiState.update { old -> old.copy(isLoading = true) }
//                val items = getBackendConfigItemsUseCase()
//                _uiState.update { old ->
//                    old.copy(
//                        isLoading = false,
//                        listItems = items
//                    )
//                }
//            } catch (e: Exception) {
//                showError(e.message ?: "Unknown error")
//            } finally {
//                _uiState.update { old -> old.copy(isLoading = false) }
//            }
//        }
//    }

    private suspend fun selectConfig(config: BackendConfig) {
        selectBackendConfigUseCase(config)
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
                    icon = MaterialDesignIconic.Icon.gmi_plus,
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