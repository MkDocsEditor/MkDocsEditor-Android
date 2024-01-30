package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme

@Composable
internal fun SearchScreenContent(
    uiState: SearchViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    SearchBar(
        modifier = Modifier
            .fillMaxSize(),
        query = uiState.currentSearchFilter,
        onQueryChange = { onUiEvent(UiEvent.SearchInputChanged(it)) },
        onSearch = { onUiEvent(UiEvent.SearchRequested(it)) },
        active = true,
        onActiveChange = { searchActive ->
            onUiEvent(
                UiEvent.SearchExpandedChanged(
                    searchActive
                )
            )
        },
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = stringResource(
                R.string.search_result_count_header,
                uiState.currentSearchResults.size
            )
        )

        SearchResultList(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 16.dp,
                    horizontal = 16.dp,
                )
                .verticalScroll(rememberScrollState()),
            items = uiState.currentSearchResults,
            onItemClicked = {
                onUiEvent(UiEvent.SearchResultClicked(it))
            },
            onItemLongClicked = {
                onUiEvent(UiEvent.SearchResultLongClicked(it))
            },
        )
    }
}

@Preview
@Composable
private fun SearchScreenContentPreview() {
    MkDocsEditorTheme {
        SearchScreenContent(
            uiState = SearchViewModel.UiState(
                currentSearchFilter = "test",
                currentSearchResults = listOf(
                    SearchResultItem.Document(
                        documentId = "documentId",
                        documentName = "documentName",
                        documentExcerpt = "Dieser Teil des Dokuments enthält den Suchbefriff \"test\".",
                    ),
                    SearchResultItem.Section(
                        sectionId = "sectionId",
                        sectionName = "sectionName",
                    ),
                    SearchResultItem.Resource(
                        resourceId = "resourceId",
                        resourceName = "resourceName",
                    ),
                ),
            ),
            onUiEvent = {},
        )
    }
}