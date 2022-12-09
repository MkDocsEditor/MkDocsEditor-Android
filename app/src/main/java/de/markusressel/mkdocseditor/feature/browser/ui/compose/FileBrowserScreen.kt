package de.markusressel.mkdocseditor.feature.browser.ui.compose

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.fengdai.compose.pulltorefresh.PullToRefresh
import com.github.fengdai.compose.pulltorefresh.rememberPullToRefreshState
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserEvent
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.browser.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.main.ui.NavigationEvent
import kotlinx.coroutines.flow.collectLatest

@Composable
internal fun FileBrowserScreen(
    onNavigationEvent: (NavigationEvent) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FileBrowserViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    BackHandler(
        enabled = uiState.canGoUp,
        onBack = {
            val consumed = viewModel.navigateUp()
            if (consumed.not()) {
                onBack()
            }
        },
    )

    // Runs only on initial composition
    LaunchedEffect(key1 = Unit) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is FileBrowserEvent.ErrorEvent -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is FileBrowserEvent.OpenDocumentEditorEvent -> {
                    onNavigationEvent(NavigationEvent.NavigateToCodeEditor(documentId = event.entity.id))
                }
                is FileBrowserEvent.DownloadResourceEvent -> {
                    // TODO: download resource
                    Toast.makeText(context, "Not implemented :(", Toast.LENGTH_SHORT)
                        .show()
                }
                is FileBrowserEvent.ReloadEvent -> {
                    // showEmpty()
                }
                is FileBrowserEvent.CreateDocumentEvent -> {
                    Toast.makeText(context, "Not implemented :(", Toast.LENGTH_SHORT)
                        .show()
                    // TODO:
//                        val existingSections = emptyList<String>()
//
//                        MaterialDialog(context()).show {
//                            lifecycleOwner(this@FileBrowserFragment)
//                            title(R.string.create_document)
//                            input(
//                                waitForPositiveButton = false,
//                                allowEmpty = false,
//                                hintRes = R.string.hint_new_section,
//                                inputType = InputType.TYPE_CLASS_TEXT
//                            ) { dialog, text ->
//
//                                val trimmedText = text.toString().trim()
//
//                                val inputField = dialog.getInputField()
//                                val isValid = !existingSections.contains(trimmedText)
//
//                                inputField.error = when (isValid) {
//                                    true -> null
//                                    false -> getString(R.string.error_section_already_exists)
//                                }
//                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
//                            }
//
//                            positiveButton(android.R.string.ok, click = {
//                                val documentName = getInputField().text.toString().trim()
//                                viewModel.createNewDocument(documentName)
//                            })
//                            negativeButton(android.R.string.cancel)
//                        }
                }
                is FileBrowserEvent.CreateSectionEvent -> {
                    // TODO:
                    Toast.makeText(context, "Not implemented :(", Toast.LENGTH_SHORT)
                        .show()
//                        val existingSections = emptyList<String>()
//
//                        MaterialDialog(context()).show {
//                            lifecycleOwner(this@FileBrowserFragment)
//                            title(R.string.create_section)
//                            input(
//                                waitForPositiveButton = false,
//                                allowEmpty = false,
//                                hintRes = R.string.hint_new_section,
//                                inputType = InputType.TYPE_CLASS_TEXT
//                            ) { dialog, text ->
//
//                                val trimmedText = text.toString().trim()
//
//                                val inputField = dialog.getInputField()
//                                val isValid = !existingSections.contains(trimmedText)
//
//                                inputField.error = when (isValid) {
//                                    true -> null
//                                    false -> getString(R.string.error_section_already_exists)
//                                }
//                                dialog.setActionButtonEnabled(WhichButton.POSITIVE, isValid)
//                            }
//
//                            positiveButton(android.R.string.ok, click = {
//                                val sectionName = getInputField().text.toString().trim()
//                                viewModel.createNewSection(sectionName)
//                            })
//                            negativeButton(android.R.string.cancel)
//                        }
                }
                is FileBrowserEvent.RenameDocumentEvent -> {
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
        modifier = modifier,
        uiState = uiState,
        onUiEvent = viewModel::onUiEvent
    )
}


@Preview
@Composable
private fun FileBrowserScreenContentPreview() {
    FileBrowserScreenContent(
        uiState = UiState(
            listItems = listOf(

            )
        ),
        onUiEvent = {}
    )
}

@Composable
private fun FileBrowserScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        PullToRefresh(
            modifier = modifier,
            state = rememberPullToRefreshState(
                isRefreshing = uiState.isLoading
            ),
            onRefresh = { onUiEvent(UiEvent.Refresh) },
        ) {
            Column {
                if (uiState.error != null) {
                    Text(text = uiState.error)
                }

                FileBrowserList(
                    items = uiState.listItems,
                    onDocumentClicked = {
                        onUiEvent(UiEvent.DocumentClicked(it))
                    },
                    onResourceClicked = {
                        onUiEvent(UiEvent.ResourceClicked(it))
                    },
                    onSectionClicked = {
                        onUiEvent(UiEvent.SectionClicked(it))
                    },
                )
            }
        }

        ExpandableFab(
            modifier = Modifier.fillMaxSize(),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
    }
}