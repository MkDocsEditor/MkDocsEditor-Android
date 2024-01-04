package de.markusressel.mkdocseditor.ui.activity

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

sealed class SnackbarData {
    data object ConnectionFailed : SnackbarData()
    data object Disconnected : SnackbarData()
}

internal data class UiState(
    val drawerNavItems: List<NavItem> = listOf(
        NavItem.BackendSelection,
        NavItem.FileBrowser,
        NavItem.Settings,
        NavItem.About,
    ),

    val bottomBarNavItems: List<NavItem> = listOf(
        NavItem.BackendSelection,
        NavItem.FileBrowser,
        NavItem.Settings,
        NavItem.About,
    ),

    val snackbar: SnackbarData? = null
)

internal sealed class UiEvent {
}

@HiltViewModel
internal class MainViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    internal val uiState = _uiState.asStateFlow()

}