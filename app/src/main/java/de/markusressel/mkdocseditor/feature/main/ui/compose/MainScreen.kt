package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.markusressel.mkdocseditor.feature.about.ui.compose.AboutScreen
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.compose.FileBrowserScreen
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.main.ui.*
import de.markusressel.mkdocseditor.feature.preferences.ui.compose.PreferencesScreen
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.activity.MainViewModel
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun MainScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    onBack: () -> Unit,
    windowSize: WindowWidthSizeClass,
    devicePosture: DevicePosture,
) {
    val uiState by mainViewModel.uiState.collectAsState()

    MainScreenLayout(
        uiState = uiState,
        onUiEvent = mainViewModel::onUiEvent,
        onBack = onBack,
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
            onUiEvent = {},
            onBack = {},
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
            onUiEvent = {},
            onBack = {},
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
            onUiEvent = {},
            onBack = {},
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

//    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
//    LaunchedEffect(key1 = drawerState) {
//        if (drawerState.isAnimationRunning.not()
//            && drawerState.targetValue == drawerState.currentValue
//        ) {
//            val newNavDrawerOpenValue = when (drawerState.targetValue) {
//                DrawerValue.Closed -> false
//                DrawerValue.Open -> true
//            }
//
//            if (uiState.navDrawerOpen != newNavDrawerOpenValue) {
//                onUiEvent(UiEvent.ToggleNavDrawer)
//            }
//        }
//    }
//    LaunchedEffect(key1 = uiState.navDrawerOpen) {
//        launch {
//            if (uiState.navDrawerOpen) {
//                drawerState.open()
//            } else {
//                drawerState.close()
//            }
//        }
//    }

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
                )
            }
        }
        NavigationLayoutType.BOTTOM_NAVIGATION,
        NavigationLayoutType.NAVIGATION_RAIL -> {
//            ModalNavigationDrawer(
//                drawerContent = {
//                    ModalDrawerSheet {
//                        NavigationDrawerContent(
//                            navItems = uiState.drawerNavItems,
//                            selectedDestination = uiState.selectedBottomBarItem,
//                            onHamburgerIconClicked = {
//                                onUiEvent(UiEvent.ToggleNavDrawer)
//                            }
//                        )
//                    }
//                },
//                drawerState = drawerState
//            ) {
            MainScreenContent(
                navigationType = navigationType,
                contentType = contentType,
                uiState = uiState,
                onUiEvent = onUiEvent,
                onBack = onBack,
                selectedDestination = uiState.selectedBottomBarItem,
            )
//            }
        }
    }
}

@Composable
private fun MainScreenContent(
    navigationType: NavigationLayoutType,
    contentType: ContentLayoutType,
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBack: () -> Unit,
    selectedDestination: NavItem,
) {
    Box(modifier = Modifier.fillMaxSize()) {

        Row(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                modifier = Modifier.zIndex(1000f),
                visible = navigationType == NavigationLayoutType.NAVIGATION_RAIL
            ) {
                MkDocsEditorNavigationRail(
                    selectedNavItem = uiState.selectedBottomBarItem,
                    navItems = uiState.bottomBarNavItems,
                    onItemSelected = { onUiEvent(UiEvent.BottomNavItemSelected(it)) },
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.inverseOnSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {

                    when (selectedDestination) {
                        NavItem.FileBrowser -> if (contentType == ContentLayoutType.LIST_AND_DOCUMENT) {
                            MkDocsEditorListAndDocumentContent(
                                modifier = Modifier.fillMaxSize(),
                                //onNavigationEvent = onNavigationEvent,
                                mainUiState = uiState,
                                onBack = onBack,
                                onUiEvent = onUiEvent
                            )
                        } else {
                            MkDocsEditorListOnlyContent(
                                modifier = Modifier.fillMaxSize(),
                                //onNavigationEvent = onNavigationEvent,
                                mainUiState = uiState,
                                onBack = onBack,
                                onUiEvent = onUiEvent
                            )
                        }
                        NavItem.Settings -> PreferencesScreen(
                            modifier = Modifier.fillMaxSize(),
                            onBack = {
                                onUiEvent(UiEvent.BottomNavItemSelected(NavItem.FileBrowser))
                            }
                        )
                        NavItem.About -> AboutScreen(
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                AnimatedVisibility(
                    modifier = Modifier.zIndex(1000f),
                    visible = navigationType == NavigationLayoutType.BOTTOM_NAVIGATION
                ) {
                    BottomBar(
                        selectedNavItem = uiState.selectedBottomBarItem,
                        navItems = uiState.bottomBarNavItems,
                        onItemSelected = { onUiEvent(UiEvent.BottomNavItemSelected(it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MkDocsEditorListOnlyContent(
    //onNavigationEvent: (NavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    codeEditorViewModel: CodeEditorViewModel = hiltViewModel(),
    mainUiState: UiState,
    onBack: () -> Unit,
    onUiEvent: (UiEvent) -> Unit,
) {

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = mainUiState.documentId != null,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth }
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth }
            ),
        ) {
            CodeEditorScreen(
                //modifier = Modifier.background(Color.Transparent),
                mainUiState = mainUiState,
                onBack = {
                    codeEditorViewModel.onClose()
                    onUiEvent(UiEvent.CloseDocumentEditor)
                }
            )
        }

        AnimatedVisibility(
            modifier = Modifier,
            visible = mainUiState.documentId == null,
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth }
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth }
            ),
        ) {
            FileBrowserScreen(
                modifier = Modifier.background(Color.Transparent),
                onNavigationEvent = { event ->
                    when (event) {
                        is NavigationEvent.NavigateToCodeEditor -> {
                            onUiEvent(UiEvent.UpdateCurrentDocumentId(event.documentId))
                        }
                    }
                },
                onBack = onBack
            )
        }
    }
}

@Composable
private fun MkDocsEditorListAndDocumentContent(
    //onNavigationEvent: (NavigationEvent) -> Unit,
    mainUiState: UiState,
    modifier: Modifier = Modifier,
    fileBrowserViewModel: FileBrowserViewModel = hiltViewModel(),
    codeEditorViewModel: CodeEditorViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onUiEvent: (UiEvent) -> Unit,
) {
    val uiState by fileBrowserViewModel.uiState.collectAsState()

    Row(modifier = modifier) {
        FileBrowserScreen(
            modifier = modifier.weight(0.33f),
            onNavigationEvent = { event ->
                when (event) {
                    is NavigationEvent.NavigateToCodeEditor -> {
                        onUiEvent(UiEvent.UpdateCurrentDocumentId(event.documentId))
                    }
                }
            },
            onBack = onBack,
        )

        val documentId by codeEditorViewModel.documentId.collectAsState()

        AnimatedVisibility(
            visible = documentId != null,
            enter = expandHorizontally(
                expandFrom = Alignment.Start,
            ),
            exit = shrinkHorizontally(
                shrinkTowards = Alignment.End,
            ),
        ) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = "codeEditor/${documentId}") {
                composable(
                    "codeEditor/{documentId}",
                    arguments = listOf(navArgument("documentId") { type = NavType.StringType })
                ) {
                    CodeEditorScreen(
                        modifier = Modifier,
                        mainUiState = mainUiState,
                        onBack = {
                            codeEditorViewModel.onClose()
                            onUiEvent(UiEvent.CloseDocumentEditor)
                        },
                    )
                }
            }
        }
    }
}
