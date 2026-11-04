package de.markusressel.mkdocseditor.feature.backendconfig.list.ui

import androidx.lifecycle.ViewModel
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.R
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
    private val getBackendConfigsFlowUseCase: GetBackendConfigsFlowUseCase,
    private val selectBackendConfigUseCase: SelectBackendConfigUseCase,
) : ViewModel() {

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

                is UiEvent.BackendConfigEditClicked -> {
                    editConfig(event.config)
                }

                is UiEvent.ExpandableFabItemSelected -> {
                    when (event.item.id) {
                        BackendSelectionFabId.CreateBackendConfig -> {
                            navigateToCreateBackendConfig()
                        }
                    }
                }
            }
        }
    }

    private suspend fun selectConfig(config: BackendConfig) {
        selectBackendConfigUseCase(config)
    }

    private suspend fun editConfig(config: BackendConfig) {
        _events.send(BackendSelectionEvent.EditBackend(config.id))
    }


    private suspend fun navigateToCreateBackendConfig() {
        _events.send(BackendSelectionEvent.CreateBackend)
    }

    internal sealed class UiEvent {
        data class BackendConfigClicked(val config: BackendConfig) : UiEvent()
        data class BackendConfigEditClicked(val config: BackendConfig) : UiEvent()
        data class ExpandableFabItemSelected(val item: FabConfig.Fab<BackendSelectionFabId>) : UiEvent()
    }

    internal data class UiState(
        val fabConfig: FabConfig<BackendSelectionFabId> = FabConfig(
            right = listOf(
                FabConfig.Fab(
                    id = BackendSelectionFabId.CreateBackendConfig,
                    description = R.string.create_backend_config,
                    icon = MaterialDesignIconic.Icon.gmi_plus,
                ),
            )
        ),

        /** true until the configs were read once */
        val isLoading: Boolean = true,
        val listItems: List<BackendConfig> = emptyList(),
    )

    internal sealed class BackendSelectionEvent {
        data object CreateBackend : BackendSelectionEvent()
        data class EditBackend(val id: Long) : BackendSelectionEvent()
    }
}

sealed class BackendSelectionFabId {
    data object CreateBackendConfig : BackendSelectionFabId()
}