package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.android.textResource
import de.markusressel.mkdocseditor.feature.auth.account.ui.compose.AccountScreen
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.filebrowser.ui.DialogState
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserEvent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.CreateDocumentDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.CreateSectionDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.BacklinksDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.DeleteConfirmationDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.ItemActionsDialog
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.MoveDialog
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filepicker.ui.compose.FilePickerScreen
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchScreen
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.runBlocking

object FileBrowserScreen : Screen {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = getViewModel<FileBrowserViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        BackHandler(
            enabled = uiState.canGoUp,
            onBack = {
                val consumed = runBlocking {
                    viewModel.navigateUp()
                }
                if (consumed.not()) {
                    navigator.pop()
                }
            }
        )

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
                        Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        FileBrowserScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )

        when (val dialogState = uiState.currentDialogState) {
            is DialogState.CreateDocument -> {
                CreateDocumentDialog(
                    uiState = dialogState,
                    onSaveClicked = { text ->
                        viewModel.onUiEvent(
                            UiEvent.CreateDocumentDialogSaveClicked(
                                sectionId = dialogState.sectionId,
                                name = text
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            is DialogState.CreateSection -> {
                CreateSectionDialog(
                    uiState = dialogState,
                    onSaveClicked = { text ->
                        viewModel.onUiEvent(
                            UiEvent.CreateSectionDialogSaveClicked(
                                parentSectionId = dialogState.parentSectionId,
                                name = text
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            is DialogState.ItemActions -> {
                ItemActionsDialog(
                    item = dialogState.item,
                    onAction = { viewModel.onUiEvent(UiEvent.ItemActionClicked(dialogState.item, it)) },
                    onDismissRequest = { viewModel.onUiEvent(UiEvent.DismissDialog) },
                )
            }

            is DialogState.Move -> {
                MoveDialog(
                    state = dialogState,
                    onPreview = { parentId, name -> viewModel.onUiEvent(UiEvent.MovePreviewRequested(parentId, name)) },
                    onBack = { viewModel.onUiEvent(UiEvent.MoveBackClicked) },
                    onApply = { viewModel.onUiEvent(UiEvent.MoveApplyClicked) },
                    onDismissRequest = { viewModel.onUiEvent(UiEvent.DismissDialog) },
                )
            }

            is DialogState.Backlinks -> {
                BacklinksDialog(
                    state = dialogState,
                    onOpenDocument = { viewModel.onUiEvent(UiEvent.BacklinkClicked(it)) },
                    onDismissRequest = { viewModel.onUiEvent(UiEvent.DismissDialog) },
                )
            }

            is DialogState.DeleteConfirmation -> {
                val (title, message) = when (dialogState.item) {
                    is WikiItem.Document -> R.string.delete_document_confirmation_title to R.string.delete_document_confirmation_message
                    is WikiItem.Section -> R.string.delete_section_confirmation_title to R.string.delete_section_confirmation_message
                    is WikiItem.Resource -> R.string.delete_resource_confirmation_title to R.string.delete_resource_confirmation_message
                }
                val usedBy = dialogState.usedBy
                val usedByText = when {
                    dialogState.usedByUnavailable -> null
                    usedBy == null -> stringResource(R.string.delete_used_by_loading)
                    usedBy == 0 -> stringResource(R.string.delete_used_by_none)
                    else -> pluralStringResource(R.plurals.delete_used_by_count, usedBy, usedBy)
                }
                val text = textResource(message)
                DeleteConfirmationDialog(
                    title = stringResource(title),
                    message = buildAnnotatedString {
                        append(text)
                        if (usedByText != null) {
                            append("\n\n")
                            append(usedByText)
                        }
                    },
                    onConfirmClicked = { viewModel.onUiEvent(UiEvent.DeleteConfirmed(dialogState.item)) },
                    onDismissRequest = { viewModel.onUiEvent(UiEvent.DismissDialog) },
                )
            }

            else -> {}
        }
    }


}
