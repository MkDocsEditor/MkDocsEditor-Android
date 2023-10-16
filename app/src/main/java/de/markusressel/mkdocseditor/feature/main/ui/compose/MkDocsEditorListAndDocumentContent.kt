package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.markusressel.mkdocseditor.feature.browser.ui.compose.FileBrowserScreen
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.main.ui.NavigationEvent
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState

@Composable
internal fun MkDocsEditorListAndDocumentContent(
    //onNavigationEvent: (NavigationEvent) -> Unit,
    mainUiState: UiState,
    modifier: Modifier = Modifier,
    fileBrowserUiState: de.markusressel.mkdocseditor.feature.browser.ui.UiState,
    codeEditorUiState: de.markusressel.mkdocseditor.feature.editor.ui.UiState,
    onBack: () -> Unit,
    onUiEvent: (UiEvent) -> Unit,
) {
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

        val documentId = codeEditorUiState.documentId

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
                            onUiEvent(UiEvent.CloseDocumentEditor)
                        },
                    )
                }
            }
        }
    }
}