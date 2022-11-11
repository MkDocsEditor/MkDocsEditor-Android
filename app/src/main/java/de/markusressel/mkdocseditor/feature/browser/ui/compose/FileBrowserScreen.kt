package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.ui.compose.ExpandableFab

@Composable
internal fun FileBrowserScreen(
    viewModel: FileBrowserViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = Modifier) {
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

        ExpandableFab(
            modifier = Modifier.fillMaxSize(),
            items = viewModel.fabConfig.right
        )
    }
}
