package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.edit.domain.AddBackendConfigItemsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class BackendConfigEditViewModel @Inject constructor(
    private val addBackendConfigItemsUseCase: AddBackendConfigItemsUseCase,
) : ViewModel() {

    // TODO: use savedState
    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    private val _events = Channel<BackendEditEvent>(Channel.BUFFERED)
    internal val events = _events.receiveAsFlow()

    fun onUiEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.SaveClicked -> {
                    // TODO
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
    }

    internal data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val currentConfig: BackendConfig? = null,
    )

    internal sealed class BackendEditEvent {
        data class Error(val message: String) : BackendEditEvent()
    }
}
