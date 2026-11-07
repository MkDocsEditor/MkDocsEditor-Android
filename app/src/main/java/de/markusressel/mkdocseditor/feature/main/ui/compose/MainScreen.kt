package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import de.markusressel.mkdocseditor.feature.main.ui.DevicePosture
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.NavigationLayoutType
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.MainViewModel
import de.markusressel.mkdocseditor.ui.activity.UiState
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture,
) {
    val uiState by mainViewModel.uiState.collectAsState()

    MainScreenLayout(
        uiState = uiState,
        fileBrowserRequests = mainViewModel.fileBrowserRequests,
        windowSize = windowSize,
        devicePosture = devicePosture,
    )
}

@CombinedPreview
@Composable
private fun MainScreenPreview() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(initialTab = NavItem.FileBrowser),
            fileBrowserRequests = emptyFlow(),
            windowSize = WindowWidthSizeClass.Compact,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@CombinedPreview
@Composable
private fun MainScreenPreviewTablet() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(initialTab = NavItem.FileBrowser),
            fileBrowserRequests = emptyFlow(),
            windowSize = WindowWidthSizeClass.Medium,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@CombinedPreview
@Composable
private fun MainScreenPreviewDesktop() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(initialTab = NavItem.FileBrowser),
            fileBrowserRequests = emptyFlow(),
            windowSize = WindowWidthSizeClass.Expanded,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@Composable
private fun MainScreenLayout(
    uiState: UiState,
    fileBrowserRequests: Flow<Unit>,
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture,
) {
    when (val navigationType = determineNavigationType(windowSize, devicePosture)) {
        NavigationLayoutType.BOTTOM_NAVIGATION,
        NavigationLayoutType.NAVIGATION_RAIL -> {
            MainScreenContent(
                navigationType = navigationType,
                uiState = uiState,
                fileBrowserRequests = fileBrowserRequests,
            )
        }

        else -> {}
    }
}

private fun determineNavigationType(
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture
): NavigationLayoutType = when (windowSize) {
    WindowWidthSizeClass.Compact -> NavigationLayoutType.BOTTOM_NAVIGATION
    WindowWidthSizeClass.Medium -> NavigationLayoutType.NAVIGATION_RAIL
    WindowWidthSizeClass.Expanded -> if (devicePosture is DevicePosture.BookPosture) {
        NavigationLayoutType.NAVIGATION_RAIL
    } else {
        NavigationLayoutType.NAVIGATION_RAIL
        //NavigationLayoutType.PERMANENT_NAVIGATION_DRAWER
    }

    else -> NavigationLayoutType.BOTTOM_NAVIGATION
}
