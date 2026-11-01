package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.navigator.LocalNavigator
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
    val focusRequester = remember { FocusRequester() }
    val navigator = LocalNavigator.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val leadingIcon = @Composable {
        val visibilityState = remember {
            MutableTransitionState(false).apply {
                targetState = true
            }
        }

        AnimatedVisibility(
            visibleState = visibilityState,
            enter = expandHorizontally(),
            exit = shrinkHorizontally(),
        ) {
            IconButton(
                onClick = {
                    focusRequester.freeFocus()
                    navigator?.pop()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }

    // The input field gets its own modifier: with the old SearchBar overload a fillMaxSize
    // modifier let the input field take the whole screen, which hid the results
    SearchBar(
        modifier = Modifier.fillMaxWidth(),
        inputField = {
            SearchBarDefaults.InputField(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("search-input"),
                query = uiState.currentSearchFilter,
                onQueryChange = { onUiEvent(UiEvent.SearchInputChanged(it)) },
                onSearch = { onUiEvent(UiEvent.SearchRequested(it)) },
                expanded = true,
                onExpandedChange = { searchActive -> onUiEvent(UiEvent.SearchExpandedChanged(searchActive)) },
                leadingIcon = leadingIcon,
            )
        },
        expanded = true,
        onExpandedChange = { searchActive -> onUiEvent(UiEvent.SearchExpandedChanged(searchActive)) },
    ) {
        LoadingOverlay(
            modifier = Modifier.fillMaxSize(),
            isLoading = uiState.isLoading,
        ) {
            Column {
                Text(
                    modifier = Modifier.padding(8.dp),
                    text = when {
                        uiState.showRecentSearches -> stringResource(R.string.search_recent_header)
                        uiState.isInputTooShort -> stringResource(
                            R.string.search_input_too_short,
                            SearchViewModel.MIN_SEARCH_LENGTH
                        )

                        else -> stringResource(
                            R.string.search_result_count_header,
                            uiState.currentSearchResults.size
                        )
                    }
                )

                if (uiState.showRecentSearches) {
                    RecentSearchList(
                        modifier = Modifier.fillMaxWidth(),
                        terms = uiState.recentSearches,
                        onTermClicked = { onUiEvent(UiEvent.RecentSearchClicked(it)) },
                    )
                } else {
                    SearchResultList(
                        modifier = Modifier.fillMaxWidth(),
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
                                text = "Dieser Teil des Dokuments enthält den Suchbegriff test.",
                                matches = listOf(50..53),
                                heading = "Eine Überschrift",
                            )
                        ),
                        path = listOf("Ordner", "Unterordner"),
                        matchCount = 2,
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