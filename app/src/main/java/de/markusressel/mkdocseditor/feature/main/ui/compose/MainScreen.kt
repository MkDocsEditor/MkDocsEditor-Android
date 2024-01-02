package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.mkdocseditor.feature.main.ui.ContentLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.DevicePosture
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
        windowSize = windowSize,
        devicePosture = devicePosture,
    )
}

@CombinedPreview
@Composable
private fun MainScreenPreview() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
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
            uiState = UiState(),
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
            uiState = UiState(),
            windowSize = WindowWidthSizeClass.Expanded,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@Composable
private fun MainScreenLayout(
    uiState: UiState,
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture,
) {
    val (navigationType, contentType) = determineLayoutStyle(windowSize, devicePosture)

    when (navigationType) {
        NavigationLayoutType.BOTTOM_NAVIGATION,
        NavigationLayoutType.NAVIGATION_RAIL -> {
            MainScreenContent(
                navigationType = navigationType,
                contentType = contentType,
                uiState = uiState,
            )
        }

        else -> {}
    }
}

fun determineLayoutStyle(
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture
): Pair<NavigationLayoutType, ContentLayoutType> {
    val navigationType: NavigationLayoutType
    val contentType: ContentLayoutType

    when (windowSize) {
        WindowWidthSizeClass.Compact -> {
            navigationType = NavigationLayoutType.NAVIGATION_RAIL
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

    return navigationType to contentType
}

