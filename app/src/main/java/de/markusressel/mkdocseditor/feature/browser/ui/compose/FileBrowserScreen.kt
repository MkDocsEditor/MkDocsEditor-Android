package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel

@Composable
internal fun FileBrowserScreen(
    viewModel: FileBrowserViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    FileBrowserList(
        items = uiState.listItems,
        onDocumentClicked = {
            viewModel.onDocumentClicked(it)
        },
        onResourceClicked = {
            viewModel.onResourceClicked(it)
        },
        onSectionClicked = {
            viewModel.onSectionClicked(it)
        },
    )
}