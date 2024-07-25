package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserScreen

@Composable
internal fun MkDocsEditorListAndDocumentContent(
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        Navigator(FileBrowserScreen) { navigator ->
            SlideTransition(navigator)
        }

//        val documentId = codeEditorUiState.documentId

//        AnimatedVisibility(
//            visible = documentId != null,
//            enter = expandHorizontally(
//                expandFrom = Alignment.Start,
//            ),
//            exit = shrinkHorizontally(
//                shrinkTowards = Alignment.End,
//            ),
//        ) {
//            val safeDocumentId = documentId ?: return@AnimatedVisibility
//            Navigator(CodeEditorScreen(safeDocumentId)) { navigator ->
//                SlideTransition(navigator)
//            }
//        }
    }
}