package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.browser.ui.UiState
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab

@Composable
internal fun FileBrowserScreen(
    viewModel: FileBrowserViewModel,
) {
    val uiState by viewModel.uiState.collectAsState()

    FileBrowserScreenContent(
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
) {
    Box(
        modifier = Modifier.padding(
            vertical = 8.dp,
            horizontal = 8.dp,
        )
    ) {
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

        ExpandableFab(
            modifier = Modifier.fillMaxSize(),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
    }
}