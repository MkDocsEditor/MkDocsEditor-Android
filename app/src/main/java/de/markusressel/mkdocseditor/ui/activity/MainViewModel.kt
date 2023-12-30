package de.markusressel.mkdocseditor.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SnackbarData(
    val text: String,
    val action: String,
)

internal data class UiState(
    val drawerNavItems: List<NavItem> = listOf(
        NavItem.BackendSelection,
        NavItem.FileBrowser,
        NavItem.Settings,
        NavItem.About,
    ),

    val selectedBottomBarItem: NavItem = NavItem.FileBrowser,
    val bottomBarNavItems: List<NavItem> = listOf(
        NavItem.BackendSelection,
        NavItem.FileBrowser,
        NavItem.Settings,
        NavItem.About,
    ),

    val snackbar: SnackbarData? = null
)

internal sealed class UiEvent {
    data class BottomNavItemSelected(val item: NavItem) : UiEvent()
    data class DrawerNavItemClicked(val item: NavItem) : UiEvent()
    object NavigateBack : UiEvent()

    data class UpdateCurrentDocumentId(val documentId: String?) : UiEvent()
    object CloseDocumentEditor : UiEvent()

    object SnackbarActionTriggered : UiEvent()
}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

    internal fun onUiEvent(event: UiEvent) {
        when (event) {
            is UiEvent.BottomNavItemSelected -> selectBottomNavItem(event.item)
            is UiEvent.DrawerNavItemClicked -> selectBottomNavItem(event.item)
            is UiEvent.UpdateCurrentDocumentId -> {
//                 TODO
//                _uiState.value = uiState.value.copy(
//                    documentId = event.documentId
//                )
            }
            is UiEvent.CloseDocumentEditor -> {
//                 TODO
//                _uiState.value = uiState.value.copy(
//                    documentId = null
//                )
            }
            UiEvent.SnackbarActionTriggered -> onSnackbarAction()
            UiEvent.NavigateBack -> {
                Timber.d { "Back navigation handled by system" }
            }
        }
    }

    private fun onSnackbarAction() {

    }

    private fun selectBottomNavItem(item: NavItem) {
        _uiState.value = uiState.value.copy(
            selectedBottomBarItem = item
        )
    }

}