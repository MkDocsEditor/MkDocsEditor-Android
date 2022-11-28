package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.compose.FileBrowserScreen
import de.markusressel.mkdocseditor.feature.main.ui.ContentLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.DevicePosture
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.NavigationLayoutType
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.MainViewModel
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState
import kotlinx.coroutines.launch

@Composable
internal fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    windowSize: Any,
    devicePosture: DevicePosture
) {
    val uiState by mainViewModel.uiState.collectAsState()

    MainScreenLayout(
        uiState = uiState,
        onUiEvent = mainViewModel::onUiEvent,
        windowSize = windowSize,
        devicePosture = devicePosture,
    )
}


@Preview(showBackground = true)
@Composable
private fun MainScreenPreview() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
            onUiEvent = {},
            windowSize = WindowWidthSizeClass.Compact,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@Preview(showBackground = true, widthDp = 700)
@Composable
private fun MainScreenPreviewTablet() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
            onUiEvent = {},
            windowSize = WindowWidthSizeClass.Medium,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@Preview(showBackground = true, widthDp = 1000)
@Composable
private fun MainScreenPreviewDesktop() {
    MkDocsEditorTheme {
        MainScreenLayout(
            uiState = UiState(),
            onUiEvent = {},
            windowSize = WindowWidthSizeClass.Expanded,
            devicePosture = DevicePosture.NormalPosture,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreenLayout(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    windowSize: Any,
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
                NavigationLayoutType.PERMANENT_NAVIGATION_DRAWER
            }
            contentType = ContentLayoutType.LIST_AND_DOCUMENT
        }
        else -> {
            navigationType = NavigationLayoutType.BOTTOM_NAVIGATION
            contentType = ContentLayoutType.LIST_ONLY
        }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    LaunchedEffect(key1 = drawerState) {
        if (drawerState.isAnimationRunning.not()
            && drawerState.targetValue == drawerState.currentValue
        ) {
            val newNavDrawerOpenValue = when (drawerState.targetValue) {
                DrawerValue.Closed -> false
                DrawerValue.Open -> true
            }

            if (uiState.navDrawerOpen != newNavDrawerOpenValue) {
                onUiEvent(UiEvent.ToggleNavDrawer)
            }
        }
    }
    LaunchedEffect(key1 = uiState.navDrawerOpen) {
        launch {
            if (uiState.navDrawerOpen) {
                drawerState.open()
            } else {
                drawerState.close()
            }
        }
    }

    val selectedDestination = NavItem.FileBrowser

    if (navigationType == NavigationLayoutType.PERMANENT_NAVIGATION_DRAWER) {
        PermanentNavigationDrawer(
            drawerContent = {
                PermanentDrawerSheet {
                    NavigationDrawerContent(
                        selectedDestination = selectedDestination,
                        onHamburgerIconClicked = {
                            //onUiEvent(UiEvent.DrawerNavItemClicked(it))
                        }
                    )
                }
            }
        ) {
            MainScreenContent(
                navigationType = navigationType,
                contentType = contentType,
                uiState = uiState,
                onUiEvent = onUiEvent
            )
        }
    } else {
        ModalNavigationDrawer(
            drawerContent = {
                ModalDrawerSheet {
                    NavigationDrawerContent(
                        selectedDestination,
                        onHamburgerIconClicked = {
                            onUiEvent(UiEvent.ToggleNavDrawer)
                        }
                    )
                }
            },
            drawerState = drawerState
        ) {
            MainScreenContent(
                navigationType = navigationType,
                contentType = contentType,
                uiState = uiState,
                onUiEvent = onUiEvent,
            )
        }
    }
}

@Composable
private fun MainScreenContent(
    navigationType: NavigationLayoutType,
    contentType: ContentLayoutType,
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = navigationType == NavigationLayoutType.NAVIGATION_RAIL) {
            MkDocsEditorNavigationRail(
                selectedNavItem = uiState.selectedBottomBarItem,
                navItems = uiState.bottomBarNavItems,
                onItemSelected = { onUiEvent(UiEvent.BottomNavItemSelected(it)) },
                onToggleMenu = { onUiEvent(UiEvent.ToggleNavDrawer) },
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.inverseOnSurface)
        ) {
            if (contentType == ContentLayoutType.LIST_AND_DOCUMENT) {
                MkDocsEditorListAndDocumentContent(
                    uiState = uiState,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MkDocsEditorListOnlyContent(
                    uiState = uiState,
                    modifier = Modifier.weight(1f)
                )
            }

            AnimatedVisibility(visible = navigationType == NavigationLayoutType.BOTTOM_NAVIGATION) {
                BottomBar(
                    selectedNavItem = uiState.selectedBottomBarItem,
                    navItems = uiState.bottomBarNavItems,
                    onItemSelected = { onUiEvent(UiEvent.BottomNavItemSelected(it)) },
                )
            }
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    selectedDestination: NavItem,
    onHamburgerIconClicked: (NavItem) -> Unit,
) {
    Text(text = "Navigation Drawer Content")
}

@Composable
private fun MkDocsEditorListOnlyContent(
    uiState: UiState,
    modifier: Modifier = Modifier,
) {
    FileBrowserScreen(
        modifier = modifier
    )
}

@Composable
private fun MkDocsEditorListAndDocumentContent(
    uiState: UiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Text(text = "List And Document")
    }
}