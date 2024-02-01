package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.LoadingOverlay
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

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
        LoadingOverlay(
            modifier = Modifier.fillMaxSize(),
            isLoading = uiState.isLoading,
        ) {
            Column {
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
                    searchTerm = uiState.currentSearchFilter,
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
    }
}


@CombinedPreview
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
                        excerpts = listOf(
                            SearchResultItem.Document.ExcerptData(
                                charsBefore = 10,
                                excerpt = AnnotatedString("Dieser Teil des Dokuments enthält den Suchbefriff \"test\"."),
                                charsAfter = 10,
                            )
                        ),
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