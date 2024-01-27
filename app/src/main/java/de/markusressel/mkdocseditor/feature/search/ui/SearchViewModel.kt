package de.markusressel.mkdocseditor.feature.search.ui

import androidx.core.text.trimmedLength
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.SearchUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SearchViewModel @Inject constructor(
    private val searchUseCase: SearchUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState = _uiState.asStateFlow()

    init {
        launch {
            uiState.map {
                it.currentSearchFilter
            }.distinctUntilChanged().collectLatest { currentSearchFilter ->
                val searchResults = when {
                    currentSearchFilter.trimmedLength() > 0 -> searchUseCase(
                        currentSearchFilter
                    )

                    else -> emptyList()
                }
                _uiState.update { old ->
                    old.copy(currentSearchResults = searchResults)
                }
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        viewModelScope.launch {
            when (event) {
                is UiEvent.SearchInputChanged -> {
                    setSearch(event.searchFilter)
                }

                is UiEvent.SearchExpandedChanged -> onSearchExpandedChanged(event.isSearchExpanded)
                is UiEvent.SearchRequested -> onSearchRequested(event.searchFilter)
                is UiEvent.SearchResultClicked -> onSearchResultClicked(event.searchResultItem)
            }
        }
    }

    private fun onSearchResultClicked(searchResultItem: SearchResultItem) {
        when (searchResultItem) {
            is SearchResultItem.Section -> {
                // TODO: close search, navigate to section
            }

            is SearchResultItem.Document -> {
                // TODO: close search, navigate to section containing the document, open the document in the editor
            }

            is SearchResultItem.Resource -> {
                // TODO: close search, navigate to section containing the resource, "open" the resource
            }
        }
    }

    private fun onSearchExpandedChanged(searchExpanded: Boolean) {
        if (searchExpanded.not()) {
            clearSearch()
        }
    }


    /**
     * Set the search string
     *
     * @return true if the value has changed, false otherwise
     */
    private fun setSearch(text: String): Boolean {
        return if (uiState.value.currentSearchFilter != text) {
            _uiState.update { old ->
                old.copy(
                    currentSearchFilter = text,
                )
            }
            true
        } else false
    }

    private fun clearSearch() {
        setSearch("")
    }

    private fun onSearchRequested(query: String) {
        setSearch(query)
    }

    data class UiState(
        val currentSearchFilter: String = "",
        val currentSearchResults: List<SearchResultItem> = emptyList(),
    )

    sealed class UiEvent {
        data class SearchInputChanged(val searchFilter: String) : UiEvent()
        data class SearchRequested(val searchFilter: String) : UiEvent()
        data class SearchExpandedChanged(val isSearchExpanded: Boolean) : UiEvent()

        data class SearchResultClicked(val searchResultItem: SearchResultItem) : UiEvent()
    }

}