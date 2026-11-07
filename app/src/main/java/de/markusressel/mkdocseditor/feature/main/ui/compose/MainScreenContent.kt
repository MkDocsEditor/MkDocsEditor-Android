package de.markusressel.mkdocseditor.feature.main.ui.compose


import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import de.markusressel.mkdocseditor.feature.common.ui.compose.barsAndCutout
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.NavigationLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.AboutTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.BackendSelectionTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.DashboardTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.FileBrowserTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.SettingsTab
import de.markusressel.mkdocseditor.ui.activity.MainViewModel
import de.markusressel.mkdocseditor.ui.activity.UiState
import kotlinx.coroutines.flow.Flow

val LocalMainScreenOnBack = staticCompositionLocalOf<() -> Unit> { {} }

/** the tab the app starts with, where back leaves the app */
internal val LocalMainScreenStartTab = staticCompositionLocalOf<NavItem?> { null }

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
internal fun MainScreenContent(
    navigationType: NavigationLayoutType,
    uiState: UiState,
    fileBrowserRequests: Flow<Unit>,
) {
    val navigator = LocalNavigator.current
    var globalTabNavigator: TabNavigator? by remember {
        mutableStateOf(null)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val onBackPressedCallback = {
            onBackPressed(
                navigator = navigator,
                tabNavigator = globalTabNavigator,
                initialTab = uiState.initialTab,
            )
        }

        CompositionLocalProvider(
            LocalMainScreenOnBack provides onBackPressedCallback,
            LocalMainScreenStartTab provides uiState.initialTab,
        ) {
            MainTabNavigator(
                initialTab = uiState.initialTab.toTab(),
                fileBrowserRequests = fileBrowserRequests,
            ) { tabNavigator ->
                globalTabNavigator = tabNavigator

                // stays while the keyboard is open: the keyboard slides over it and uncovers it again,
                // and a bar that comes and goes would make the screen jump
                Scaffold(
                    bottomBar = {
                        if (navigationType == NavigationLayoutType.BOTTOM_NAVIGATION) {
                            BottomBar(
                                selectedNavItem = tabNavigator.current.toNavItem(),
                                // a phone has little room: the about page opens from the settings there
                                navItems = uiState.bottomBarNavItems.filterIsInstance<NavItem.Primary>(),
                                onItemSelected = { navItem ->
                                    tabNavigator.current = navItem.toTab()
                                },
                            )
                        }
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .padding(bottom = paddingValues.calculateBottomPadding())
                            // imePadding() further down only adds what is not covered here yet
                            .consumeWindowInsets(PaddingValues(bottom = paddingValues.calculateBottomPadding()))
                    ) {
                        Row {
                            // Navigation Rail
                            AnimatedVisibility(
                                modifier = Modifier.zIndex(1000f),
                                visible = navigationType == NavigationLayoutType.NAVIGATION_RAIL
                            ) {
                                MkDocsEditorNavigationRail(
                                    selectedNavItem = tabNavigator.current.toNavItem(),
                                    navItems = uiState.bottomBarNavItems,
                                    onItemSelected = { navItem ->
                                        tabNavigator.current = navItem.toTab()
                                    },
                                )
                            }

                            // Actual tab content
                            ContentBesideRail(railShown = navigationType == NavigationLayoutType.NAVIGATION_RAIL) {
                                CurrentTab()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The tabs of the main screen. Their navigator, with the screens open in each tab, survives the recreation of the
 * activity, for example by a rotation. A request for the file browser shows it once, see [MainViewModel.fileBrowserRequests].
 */
@Composable
internal fun MainTabNavigator(
    initialTab: Tab,
    fileBrowserRequests: Flow<Unit>,
    fileBrowserTab: Tab = FileBrowserTab,
    content: @Composable (TabNavigator) -> Unit,
) {
    TabNavigator(tab = initialTab) { tabNavigator ->
        LaunchedEffect(tabNavigator, fileBrowserRequests) {
            fileBrowserRequests.collect { tabNavigator.current = fileBrowserTab }
        }
        content(tabNavigator)
    }
}

/**
 * The content next to the navigation rail. It does not touch the start of the screen then, so it must not make room
 * for a navigation bar or a cutout there a second time: the rail already does.
 */
@Composable
internal fun ContentBesideRail(
    railShown: Boolean,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = if (railShown) {
            Modifier.consumeWindowInsets(WindowInsets.barsAndCutout.only(WindowInsetsSides.Start))
        } else {
            Modifier
        }
    ) {
        content()
    }
}

private fun onBackPressed(
    navigator: Navigator?,
    tabNavigator: TabNavigator?,
    initialTab: NavItem,
) {
    if (navigator != null && navigator.pop()) {
        val consumed = navigator.popUntil { it is BackendSelectionTab || it is DashboardTab || it is FileBrowserTab }
        if (consumed || tabNavigator == null) {
            return
        }
    }
    if (tabNavigator != null) {
        tabNavigator.current = initialTab.toTab()
    }
}

private fun NavItem.toTab(): Tab = when (this) {
    is NavItem.BackendSelection -> BackendSelectionTab
    is NavItem.Dashboard -> DashboardTab
    is NavItem.FileBrowser -> FileBrowserTab
    is NavItem.Settings -> SettingsTab
    is NavItem.About -> AboutTab
}

private fun Tab.toNavItem(): NavItem = when (this) {
    is BackendSelectionTab -> NavItem.BackendSelection
    is DashboardTab -> NavItem.Dashboard
    is FileBrowserTab -> NavItem.FileBrowser
    is SettingsTab -> NavItem.Settings
    is AboutTab -> NavItem.About
    else -> throw IllegalStateException("Unknown tab: ${this}")
}
