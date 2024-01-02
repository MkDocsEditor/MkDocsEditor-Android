package de.markusressel.mkdocseditor.feature.browser.ui.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.browser.ui.DialogState
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserEvent
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
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
                // FIXME: cannot use a coroutine here
//            val consumed = viewModel.navigateUp()
//            if (consumed.not()) {
                navigator.pop()
//            }
            },
        )

        // Runs only on initial composition
        LaunchedEffect(Unit) {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is FileBrowserEvent.Error -> {
                        //Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    }

                    is FileBrowserEvent.OpenDocumentEditor -> {
                        navigator.push(CodeEditorScreen(documentId = event.documentId))
                    }

                    is FileBrowserEvent.RenameDocument -> {
                        // TODO
                        Toast.makeText(context, "Not implemented :(", Toast.LENGTH_SHORT)
                            .show()
//                        val existingDocuments = emptyList<String>()
//
//                        MaterialDialog(context()).show {
//                            lifecycleOwner(this@FileBrowserFragment)
//                            title(R.string.edit_document)
//                            input(
//                                waitForPositiveButton = false,
//                                allowEmpty = false,
//                                prefill = event.entity.name,
//                                inputType = InputType.TYPE_CLASS_TEXT
//                            ) { dialog, text ->
//
//                                val trimmedText = text.toString().trim()
//
//                                val inputField = dialog.getInputField()
//                                val isValid = !existingDocuments.contains(trimmedText)
//
//                                inputField.error = when (isValid) {
//                                    true -> null
//                                    false -> getString(R.string.error_document_already_exists)
//                                }
//                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
//                            }
//
//                            positiveButton(android.R.string.ok, click = {
//                                val documentName = getInputField().text.toString().trim()
//                                viewModel.renameDocument(event.entity.id, documentName)
//                            })
//                            neutralButton(R.string.delete, click = {
//                                viewModel.deleteDocument(event.entity.id)
//                            })
//                            negativeButton(android.R.string.cancel)
//                        }
                    }
                }
            }
        }


        FileBrowserScreenContent(
            modifier = Modifier,
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

            else -> {}
        }
    }
}
