package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel

internal data class CodeEditorScreen(
    private val documentId: String,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel: CodeEditorViewModel = hiltViewModel()
        val uiState by viewModel.uiState.collectAsState()

        BackHandler(
            enabled = true,
            onBack = { navigator.pop() },
        )

        LaunchedEffect(documentId) {
            viewModel.loadDocument(documentId)
        }

        CodeEditorScreenContent(
            modifier = Modifier,
            uiState = uiState,
            onTextChanged = {
                viewModel.onUserTextInput(
                    it.annotatedString,
                    it.selection
                )
            },
            onUiEvent = viewModel::onUiEvent
        )
    }
}
