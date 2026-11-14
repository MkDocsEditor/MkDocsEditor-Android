package de.markusressel.mkdocseditor.feature.history.ui.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.HistoryEvent
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.main.data.MainNavigationRequests
import de.markusressel.mkdocseditor.feature.main.data.editorOf
import de.markusressel.mkdocseditor.feature.main.data.rememberMainNavigationRequests

/**
 * The revision history of a document, see [HistoryViewModel]
 *
 * @param canRestore whether the account may edit the document, and so bring back an old text
 */
data class HistoryScreen(
    val documentId: String,
    val canRestore: Boolean,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val requests = rememberMainNavigationRequests()
        val viewModel = getViewModel<HistoryViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, documentId) {
            viewModel.initialize(documentId, canRestore)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is HistoryEvent.OpenInEditor -> {
                        // the history usually opens from the editor of the document, which takes the change
                        val open = navigator.items.editorOf(documentId)
                        if (open != null) {
                            navigator.popUntil { it === open }
                            requests.handChange(MainNavigationRequests.EditorChange(open.key, event.change))
                        } else {
                            navigator.replace(CodeEditorScreen(documentId = documentId, change = event.change))
                        }
                    }
                    is HistoryEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_LONG).show()
                    is HistoryEvent.Close -> navigator.pop()
                }
            }
        }

        BackHandler { viewModel.onUiEvent(UiEvent.BackClicked) }

        HistoryScreenContent(uiState = uiState, onUiEvent = viewModel::onUiEvent)
    }
}
