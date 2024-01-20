package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.SearchBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.browser.ui.UiEvent
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel

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
        active = uiState.isSearchExpanded,
        onActiveChange = { searchActive ->
            onUiEvent(
                UiEvent.SearchExpandedChanged(
                    searchActive
                )
            )
        },
    ) {
        SearchResultList(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(
                    vertical = 16.dp,
                    horizontal = 16.dp,
                ),
            items = uiState.currentSearchResults,
            onItemClicked = {
                onUiEvent(UiEvent.SearchResultClicked(it))
            },
        )
    }
}