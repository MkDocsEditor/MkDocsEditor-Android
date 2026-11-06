package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.auth.account.ui.compose.AccountScreen
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.filebrowser.ui.DialogState
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserEvent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.CreateItemDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.ItemDialogs
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.text
import de.markusressel.mkdocseditor.feature.filepicker.ui.compose.FilePickerScreen
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchScreen
import kotlinx.coroutines.flow.collectLatest

object FileBrowserScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = getViewModel<FileBrowserViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        BackHandler(
            enabled = uiState.canGoUp,
            onBack = { viewModel.onUiEvent(UiEvent.NavigateUp) },
        )

        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            // only while visible: nothing needs the socket in the background
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.followTreeChanges() }
        }

        // Runs only on initial composition
        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is FileBrowserEvent.OpenSearch -> {
                        navigator.push(SearchScreen)
                    }

                    is FileBrowserEvent.OpenDocumentEditor -> {
                        navigator.push(CodeEditorScreen(documentId = event.documentId))
                    }

                    is FileBrowserEvent.OpenAccount -> {
                        navigator.push(AccountScreen(backendId = event.backendId))
                    }

                    is FileBrowserEvent.OpenLogin -> {
                        navigator.push(LoginScreen(backendId = event.backendId))
                    }

                    is FileBrowserEvent.OpenResourceSelection -> {
                        navigator.push(FilePickerScreen())
                    }

                    is FileBrowserEvent.Toast -> {
                        Toast.makeText(context, event.message.text(context), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        FileBrowserScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )

        when (val dialogState = uiState.currentDialogState) {
            is DialogState.CreateItem -> {
                CreateItemDialog(
                    kind = dialogState.kind,
                    folder = uiState.folderContent,
                    onCreateClicked = { name ->
                        viewModel.onUiEvent(UiEvent.CreateItemConfirmed(dialogState, name))
                    },
                    onDismissRequest = { viewModel.onUiEvent(UiEvent.DismissDialog) },
                )
            }

            else -> ItemDialogs(
                dialogState = dialogState,
                onAction = { item, action -> viewModel.onUiEvent(UiEvent.ItemActionClicked(item, action)) },
                onMovePreview = { parentId, name -> viewModel.onUiEvent(UiEvent.MovePreviewRequested(parentId, name)) },
                onMoveBack = { viewModel.onUiEvent(UiEvent.MoveBackClicked) },
                onMoveApply = { viewModel.onUiEvent(UiEvent.MoveApplyClicked) },
                onBacklinkClicked = { viewModel.onUiEvent(UiEvent.BacklinkClicked(it)) },
                onDeleteConfirmed = { viewModel.onUiEvent(UiEvent.DeleteConfirmed) },
                onDismissRequest = { viewModel.onUiEvent(UiEvent.DismissDialog) },
                onPinChanged = { scope, pinned -> viewModel.onUiEvent(UiEvent.PinChanged(scope, pinned)) },
            )
        }
    }


}
