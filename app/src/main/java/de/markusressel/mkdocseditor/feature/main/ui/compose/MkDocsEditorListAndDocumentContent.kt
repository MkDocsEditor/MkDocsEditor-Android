package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.browser.ui.compose.FileBrowserScreen
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen

@Composable
internal fun MkDocsEditorListAndDocumentContent(
    modifier: Modifier = Modifier,
    codeEditorUiState: de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.UiState,
) {
    Row(modifier = modifier) {
        Navigator(FileBrowserScreen) { navigator ->
            SlideTransition(navigator)
        }

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
            val safeDocumentId = documentId ?: return@AnimatedVisibility
            Navigator(CodeEditorScreen(safeDocumentId)) { navigator ->
                SlideTransition(navigator)
            }
        }
    }
}