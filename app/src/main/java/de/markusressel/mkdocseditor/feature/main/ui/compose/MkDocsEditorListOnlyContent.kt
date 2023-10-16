package de.markusressel.mkdocseditor.feature.main.ui.compose


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.compose.FileBrowserScreen
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.main.ui.NavigationEvent
import de.markusressel.mkdocseditor.ui.activity.UiEvent
import de.markusressel.mkdocseditor.ui.activity.UiState

@Composable
internal fun MkDocsEditorListOnlyContent(
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
