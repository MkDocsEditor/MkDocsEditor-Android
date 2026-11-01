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
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.core.screen.uniqueScreenKey
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.dialog.SelectLinkTargetDialog

internal data class CodeEditorScreen(
    private val documentId: String,
) : Screen {

    /**
     * Each editor on the stack needs its own key: links in the preview open other documents
     * on top, and the key scopes the view model
     */
    override val key: ScreenKey = uniqueScreenKey

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

        LaunchedEffect(viewModel) {
            viewModel.navigationEvents.collect { request ->
                when (request) {
                    is CodeEditorViewModel.NavigationRequest.Login ->
                        navigator.push(LoginScreen(backendId = request.backendId))

                    is CodeEditorViewModel.NavigationRequest.OpenDocument ->
                        navigator.push(CodeEditorScreen(documentId = request.documentId))

                    is CodeEditorViewModel.NavigationRequest.Close -> navigator.pop()
                }
            }
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
            previewResourceLoader = viewModel.previewResourceLoader,
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
