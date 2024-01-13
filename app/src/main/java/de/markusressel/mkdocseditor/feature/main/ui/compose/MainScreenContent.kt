package de.markusressel.mkdocseditor.feature.main.ui.compose


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import de.markusressel.mkdocseditor.feature.main.ui.ContentLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.NavigationLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.AboutTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.BackendSelectionTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.FileBrowserTab
import de.markusressel.mkdocseditor.feature.main.ui.compose.tab.SettingsTab
import de.markusressel.mkdocseditor.ui.activity.UiState

@Composable
internal fun MainScreenContent(
    navigationType: NavigationLayoutType,
    contentType: ContentLayoutType,
    uiState: UiState,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        TabNavigator(uiState.initialTab.toTab(contentType)) { tabNavigator ->
            Scaffold(bottomBar = {
                if (navigationType == NavigationLayoutType.BOTTOM_NAVIGATION) {
                    BottomBar(
                        selectedNavItem = tabNavigator.current.toNavItem(),
                        navItems = uiState.bottomBarNavItems,
                        onItemSelected = { navItem ->
                            tabNavigator.current = navItem.toTab(contentType)
                        },
                    )
                }
            }) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
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
                                    tabNavigator.current = navItem.toTab(contentType)
                                },
                            )
                        }

                        // Actual tab content
                        CurrentTab()
                    }
                }
            }
        }
    }
}

private fun NavItem.toTab(contentType: ContentLayoutType): Tab = when (this) {
    is NavItem.BackendSelection -> BackendSelectionTab
    is NavItem.FileBrowser -> FileBrowserTab(contentType)
    is NavItem.Settings -> SettingsTab
    is NavItem.About -> AboutTab
}

private fun Tab.toNavItem(): NavItem = when (this) {
    is BackendSelectionTab -> NavItem.BackendSelection
    is FileBrowserTab -> NavItem.FileBrowser
    is SettingsTab -> NavItem.Settings
    is AboutTab -> NavItem.About
    else -> throw IllegalStateException("Unknown tab: ${this}")
}
