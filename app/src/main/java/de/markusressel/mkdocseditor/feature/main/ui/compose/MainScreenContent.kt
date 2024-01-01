package de.markusressel.mkdocseditor.feature.main.ui.compose


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.about.ui.compose.AboutScreen
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose.BackendConfigSelectionScreen
import de.markusressel.mkdocseditor.feature.main.ui.ContentLayoutType
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.NavigationLayoutType
import de.markusressel.mkdocseditor.feature.preferences.ui.compose.PreferencesScreen
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState

@Composable
internal fun MainScreenContent(
    fileBrowserUiState: de.markusressel.mkdocseditor.feature.browser.ui.UiState,
    codeEditorUiState: de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.UiState,
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
                        NavItem.BackendSelection -> {
                            Navigator(BackendConfigSelectionScreen) { navigator ->
                                SlideTransition(navigator)
                            }
                        }

                        NavItem.FileBrowser -> if (contentType == ContentLayoutType.LIST_AND_DOCUMENT) {
                            MkDocsEditorListAndDocumentContent(
                                modifier = Modifier.fillMaxSize(),
                                codeEditorUiState = codeEditorUiState,
                            )
                        } else {
                            MkDocsEditorListOnlyContent(
                                modifier = Modifier.fillMaxSize(),
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
