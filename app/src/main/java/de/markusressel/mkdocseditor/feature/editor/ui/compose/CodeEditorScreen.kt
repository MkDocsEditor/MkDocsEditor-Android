package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.dialog.SelectLinkTargetDialog

internal data class CodeEditorScreen(
    private val documentId: String,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = getViewModel<CodeEditorViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        BackHandler(
            enabled = true,
            onBack = { navigator.pop() },
        )

        LaunchedEffect(documentId) {
            viewModel.setCurrentDocumentId(documentId)
        }

        // persist the local copy when the app goes to the background
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) viewModel.onStop()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            onUiEvent = viewModel::onUiEvent,
            webViewActionFlow = viewModel.webViewActionFlow,
        )

        when (val dialogState = uiState.currentDialogState) {
            is CodeEditorViewModel.DialogState.SelectLinkTarget -> {
                SelectLinkTargetDialog(
                    uiState = dialogState,
                    onItemSelected = { item ->
                        viewModel.onUiEvent(
                            CodeEditorViewModel.UiEvent.ResourceSelected(resource = item)
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog)
                    },
                )
            }

            else -> {}
        }
    }
}
