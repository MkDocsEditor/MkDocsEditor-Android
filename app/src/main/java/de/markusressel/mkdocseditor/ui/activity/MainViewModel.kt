package de.markusressel.mkdocseditor.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

internal data class UiState(
    val selectedBottomBarItem: NavItem = NavItem.FileBrowser,
    val bottomBarNavItems: List<NavItem> = listOf(
        NavItem.FileBrowser,
        NavItem.Settings
    )
)

internal sealed class UiEvent {
    data class BottomNavItemSelected(val item: NavItem) : UiEvent()
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
        }
    }

    private fun selectBottomNavItem(item: NavItem) {
        _uiState.value = uiState.value.copy(
            selectedBottomBarItem = item
        )
    }

}