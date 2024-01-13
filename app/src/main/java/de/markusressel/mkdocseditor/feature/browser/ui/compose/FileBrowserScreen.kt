package de.markusressel.mkdocseditor.feature.browser.ui.compose

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
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.ui.DialogState
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserEvent
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.browser.ui.compose.dialog.CreateDocumentDialog
import de.markusressel.mkdocseditor.feature.browser.ui.compose.dialog.CreateSectionDialog
import de.markusressel.mkdocseditor.feature.browser.ui.compose.dialog.DeleteConfirmationDialog
import de.markusressel.mkdocseditor.feature.browser.ui.compose.dialog.EditDocumentDialog
import de.markusressel.mkdocseditor.feature.browser.ui.compose.dialog.EditSectionDialog
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
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
            onBack = {
                val consumed = viewModel.navigateUp()
                if (consumed.not()) {
                    navigator.pop()
                }
            }
        )

        // Runs only on initial composition
        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is FileBrowserEvent.OpenDocumentEditor -> {
                        navigator.push(CodeEditorScreen(documentId = event.documentId))
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
                                dialogState.sectionId,
                                text
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            is DialogState.EditDocument -> {
                EditDocumentDialog(
                    uiState = dialogState,
                    onDeleteClicked = {
                        viewModel.onUiEvent(
                            UiEvent.EditDocumentDialogDeleteClicked(
                                dialogState.documentId
                            )
                        )
                    },
                    onSaveClicked = { text ->
                        viewModel.onUiEvent(
                            UiEvent.EditDocumentDialogSaveClicked(
                                dialogState.documentId,
                                text
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
                                dialogState.parentSectionId,
                                text
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            is DialogState.EditSection -> {
                EditSectionDialog(
                    uiState = dialogState,
                    onDeleteClicked = {
                        viewModel.onUiEvent(
                            UiEvent.EditSectionDialogDeleteClicked(
                                dialogState.sectionId
                            )
                        )
                    },
                    onSaveClicked = { text ->
                        viewModel.onUiEvent(
                            UiEvent.EditSectionDialogSaveClicked(
                                dialogState.sectionId,
                                text
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            is DialogState.DeleteDocumentConfirmation -> {
                DeleteConfirmationDialog(
                    title = context.getString(R.string.delete_document),
                    message = context.getString(R.string.delete_document_confirmation_message),
                    onConfirmClicked = {
                        viewModel.onUiEvent(
                            UiEvent.DeleteDocumentDialogConfirmClicked(
                                dialogState.documentId
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            is DialogState.DeleteSectionConfirmation -> {
                DeleteConfirmationDialog(
                    title = context.getString(R.string.delete_section),
                    message = context.getString(R.string.delete_section_confirmation_message),
                    onConfirmClicked = {
                        viewModel.onUiEvent(
                            UiEvent.DeleteSectionDialogConfirmClicked(
                                dialogState.sectionId
                            )
                        )
                    },
                    onDismissRequest = {
                        viewModel.onUiEvent(UiEvent.DismissDialog)
                    },
                )
            }

            else -> {}
        }
    }


}
