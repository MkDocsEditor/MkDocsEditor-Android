package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.main.ui.ContentLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.DevicePosture
import de.markusressel.mkdocseditor.feature.main.ui.NavigationLayoutType
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.MainViewModel
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    codeEditorViewModel: CodeEditorViewModel = hiltViewModel(),
    fileBrowserViewModel: FileBrowserViewModel = hiltViewModel(),
    onBack: () -> Unit,
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture,
) {
    val uiState by mainViewModel.uiState.collectAsState()

    val codeEditorUiState by codeEditorViewModel.uiState.collectAsState()
    val fileBrowserUiState by fileBrowserViewModel.uiState.collectAsState()

    MainScreenLayout(
        uiState = uiState,
        onUiEvent = mainViewModel::onUiEvent,
        onBack = onBack,
        windowSize = windowSize,
        devicePosture = devicePosture,
        codeEditorUiState = codeEditorUiState,
        fileBrowserUiState = fileBrowserUiState,
    )
}

@CombinedPreview
@Composable
private fun MainScreenPreview() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
            onUiEvent = {},
            onBack = {},
            windowSize = WindowWidthSizeClass.Compact,
            devicePosture = DevicePosture.NormalPosture,
            codeEditorUiState = CodeEditorViewModel.UiState(),
            fileBrowserUiState = de.markusressel.mkdocseditor.feature.browser.ui.UiState()
        )
    }
}

@CombinedPreview
@Composable
private fun MainScreenPreviewTablet() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
            onUiEvent = {},
            onBack = {},
            windowSize = WindowWidthSizeClass.Medium,
            devicePosture = DevicePosture.NormalPosture,
            codeEditorUiState = CodeEditorViewModel.UiState(),
            fileBrowserUiState = de.markusressel.mkdocseditor.feature.browser.ui.UiState()
        )
    }
}

@CombinedPreview
@Composable
private fun MainScreenPreviewDesktop() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
            onUiEvent = {},
            onBack = {},
            windowSize = WindowWidthSizeClass.Expanded,
            devicePosture = DevicePosture.NormalPosture,
            codeEditorUiState = CodeEditorViewModel.UiState(),
            fileBrowserUiState = de.markusressel.mkdocseditor.feature.browser.ui.UiState()
        )
    }
}

@Composable
private fun MainScreenLayout(
    uiState: UiState,
    fileBrowserUiState: de.markusressel.mkdocseditor.feature.browser.ui.UiState,
    codeEditorUiState: CodeEditorViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBack: () -> Unit,
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture,
) {
    val navigationType: NavigationLayoutType
    val contentType: ContentLayoutType

    when (windowSize) {
        WindowWidthSizeClass.Compact -> {
            navigationType = NavigationLayoutType.BOTTOM_NAVIGATION
            contentType = ContentLayoutType.LIST_ONLY
        }

        WindowWidthSizeClass.Medium -> {
            navigationType = NavigationLayoutType.NAVIGATION_RAIL
            contentType = if (devicePosture != DevicePosture.NormalPosture) {
                ContentLayoutType.LIST_AND_DOCUMENT
            } else {
                ContentLayoutType.LIST_ONLY
            }
        }

        WindowWidthSizeClass.Expanded -> {
            navigationType = if (devicePosture is DevicePosture.BookPosture) {
                NavigationLayoutType.NAVIGATION_RAIL
            } else {
                NavigationLayoutType.NAVIGATION_RAIL
                //NavigationLayoutType.PERMANENT_NAVIGATION_DRAWER
            }
            contentType = ContentLayoutType.LIST_AND_DOCUMENT
        }

        else -> {
            navigationType = NavigationLayoutType.BOTTOM_NAVIGATION
            contentType = ContentLayoutType.LIST_ONLY
        }
    }

    when (navigationType) {
        NavigationLayoutType.PERMANENT_NAVIGATION_DRAWER -> {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet {
                        NavigationDrawerContent(
                            navItems = uiState.drawerNavItems,
                            selectedDestination = uiState.selectedBottomBarItem,
                            onHamburgerIconClicked = { }
                        )
                    }
                }
            ) {
                MainScreenContent(
                    navigationType = navigationType,
                    contentType = contentType,
                    uiState = uiState,
                    onUiEvent = onUiEvent,
                    onBack = onBack,
                    selectedDestination = uiState.selectedBottomBarItem,
                    codeEditorUiState = codeEditorUiState,
                    fileBrowserUiState = fileBrowserUiState,
                )
            }
        }

        NavigationLayoutType.BOTTOM_NAVIGATION,
        NavigationLayoutType.NAVIGATION_RAIL -> {
            MainScreenContent(
                navigationType = navigationType,
                contentType = contentType,
                uiState = uiState,
                onUiEvent = onUiEvent,
                onBack = onBack,
                selectedDestination = uiState.selectedBottomBarItem,
                codeEditorUiState = codeEditorUiState,
                fileBrowserUiState = fileBrowserUiState,
            )
        }
    }
}

