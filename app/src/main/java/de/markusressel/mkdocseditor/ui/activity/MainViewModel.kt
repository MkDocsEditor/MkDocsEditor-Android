package de.markusressel.mkdocseditor.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigFlowUseCase
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetInitialTabUseCase
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

sealed class SnackbarData {
    data object ConnectionFailed : SnackbarData()
    data object Disconnected : SnackbarData()

    /** the server rejected the session; local edits are kept */
    data class SignedOut(val backendId: Long) : SnackbarData()

    /** a short note without an action */
    data class Message(val text: String) : SnackbarData()
}

internal data class UiState(

    val bottomBarNavItems: List<NavItem> = listOf(
        NavItem.BackendSelection,
        NavItem.FileBrowser,
        NavItem.Settings,
        NavItem.About,
    ),

    val snackbar: SnackbarData? = null,

    val initialTab: NavItem,

    /** counts up to switch to the file browser, see [MainNavigationRequests] */
    val fileBrowserRequest: Int = 0,
)

internal sealed class UiEvent {}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val getInitialTabUseCase: GetInitialTabUseCase,
    private val getCurrentBackendConfigFlowUseCase: GetCurrentBackendConfigFlowUseCase,
    private val mainNavigationRequests: MainNavigationRequests,
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
        launch {
            mainNavigationRequests.showFileBrowser.collect {
                // the nav items must include the file browser before it is selected
                getCurrentBackendConfigFlowUseCase().first()?.let { config -> updateNavItems(config) }
                _uiState.update { old -> old.copy(fileBrowserRequest = old.fileBrowserRequest + 1) }
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