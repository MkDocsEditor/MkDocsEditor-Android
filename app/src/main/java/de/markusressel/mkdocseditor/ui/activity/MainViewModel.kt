package de.markusressel.mkdocseditor.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigFlowUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetInitialTabUseCase
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

sealed class SnackbarData {
    data object ConnectionFailed : SnackbarData()
    data object Disconnected : SnackbarData()
}

internal data class UiState(

    val bottomBarNavItems: List<NavItem> = listOf(
        NavItem.BackendSelection,
        NavItem.FileBrowser,
        NavItem.Settings,
        NavItem.About,
    ),

    val snackbar: SnackbarData? = null,

    val initialTab: NavItem
)

internal sealed class UiEvent {}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getInitialTabUseCase: GetInitialTabUseCase,
    private val getCurrentBackendConfigFlowUseCase: GetCurrentBackendConfigFlowUseCase,
) : ViewModel() {

    private val _uiState = runBlocking {
        MutableStateFlow(
            UiState(
                bottomBarNavItems = listOf(
                    NavItem.BackendSelection,
                    NavItem.FileBrowser,
                    NavItem.Settings,
                    NavItem.About,
                ),
                initialTab = getInitialTabUseCase(),
            )
        )
    }

    internal val uiState = _uiState.asStateFlow()

    init {
        launch {
            getCurrentBackendConfigFlowUseCase().collect {
                updateNavItems(it)
            }
        }
    }

    private suspend fun updateNavItems(backendConfig: BackendConfig?) {
        val newNavItems = when {
            backendConfig != null -> listOf(
                NavItem.BackendSelection,
                NavItem.FileBrowser,
                NavItem.Settings,
                NavItem.About,
            )

            else -> listOf(
                NavItem.BackendSelection,
                NavItem.Settings,
                NavItem.About,
            )
        }
        _uiState.update { old ->
            old.copy(
                bottomBarNavItems = newNavItems,
                initialTab = getInitialTabUseCase(),
            )
        }
    }

}