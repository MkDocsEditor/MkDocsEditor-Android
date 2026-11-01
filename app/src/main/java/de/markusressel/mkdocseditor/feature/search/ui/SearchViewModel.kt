package de.markusressel.mkdocseditor.feature.search.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import dagger.hilt.android.lifecycle.HiltViewModel
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.extensions.common.android.launch
import de.markusressel.mkdocseditor.feature.search.data.RecentSearchStore
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.SearchUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
internal class SearchViewModel(
    private val search: suspend (String) -> List<SearchResultItem>,
    private val eventBusManager: EventBusManager,
    private val recentSearchStore: RecentSearchStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    @Inject
    constructor(
        searchUseCase: SearchUseCase,
        eventBusManager: EventBusManager,
        recentSearchStore: RecentSearchStore,
        savedStateHandle: SavedStateHandle,
    ) : this(searchUseCase::invoke, eventBusManager, recentSearchStore, savedStateHandle)

    private val _events = Channel<UiAction>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var savedSearchFilter by savedStateHandle.saved { "" }

    private val _uiState = MutableStateFlow(
        UiState(
            recentSearches = recentSearchStore.load(),
            currentSearchFilter = savedSearchFilter,
            // a restored search shows its results, not the recent searches
            inputChanged = savedSearchFilter.isNotEmpty(),
        )
    )
    val uiState = _uiState.asStateFlow()

    init {
        launch {
            uiState.map {
                it.currentSearchFilter
            }.distinctUntilChanged().onEach {
                _uiState.update { old ->
                    old.copy(isLoading = it.isSearchable())
                }
            }.debounce(300).collectLatest { currentSearchFilter ->
                val searchResults = when {
                    currentSearchFilter.isSearchable() -> search(currentSearchFilter)

                    else -> emptyList()
                }
                _uiState.update { old ->
                    old.copy(
                        currentSearchResults = searchResults,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun onUiEvent(event: UiEvent) {
        launch {
            when (event) {
                is UiEvent.SearchInputChanged -> setSearch(event.searchFilter)
                is UiEvent.SearchExpandedChanged -> onSearchExpandedChanged(event.isSearchExpanded)
                is UiEvent.SearchRequested -> onSearchRequested(event.searchFilter)
                is UiEvent.RecentSearchClicked -> onSearchRequested(event.term)
                is UiEvent.SearchResultClicked -> onSearchResultClicked(event.searchResultItem)
                is UiEvent.SearchResultLongClicked -> onSearchResultLongClicked(event.searchResultItem)
            }
        }
    }

    private suspend fun onSearchResultClicked(searchResultItem: SearchResultItem) {
        rememberSearch(uiState.value.currentSearchFilter)
        when (searchResultItem) {
            is SearchResultItem.Section -> {
                _events.send(UiAction.NavigateBack)
                eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToSection(searchResultItem.sectionId))
            }

            is SearchResultItem.Document -> {
                // the editor replaces the search, and going back from it shows the folder
                _events.send(UiAction.OpenDocument(searchResultItem.documentId))
                eventBusManager.send(BusEvent.CodeEditorBusEvent.RevealDocument(searchResultItem.documentId))
            }

            is SearchResultItem.Resource -> {
                _events.send(UiAction.NavigateBack)
                eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToResource(searchResultItem.resourceId))
            }
        }
    }

    private fun onSearchResultLongClicked(searchResultItem: SearchResultItem) {
        when (searchResultItem) {
            is SearchResultItem.Section -> {

            }

            is SearchResultItem.Document -> {

            }

            is SearchResultItem.Resource -> {


            }
        }
    }

    private suspend fun onSearchExpandedChanged(searchExpanded: Boolean) {
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
            savedSearchFilter = text
            _uiState.update { old ->
                old.copy(
                    currentSearchFilter = text,
                    inputChanged = true,
                )
            }
            true
        } else false
    }

    private suspend fun clearSearch() {
        setSearch("")
        _events.send(UiAction.NavigateBack)
    }

    private fun onSearchRequested(query: String) {
        setSearch(query)
        rememberSearch(query)
    }

    /** a search counts when it was submitted or led somewhere, not for every typed character */
    private fun rememberSearch(term: String) {
        if (!term.isSearchable()) return
        val terms = recentSearchStore.remember(term)
        _uiState.update { old -> old.copy(recentSearches = terms) }
    }

    data class UiState(
        val isLoading: Boolean = false,
        val currentSearchFilter: String = "",
        val currentSearchResults: List<SearchResultItem> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        /** true once the input was changed, the recent searches are only offered before that */
        val inputChanged: Boolean = false,
    ) {
        val showRecentSearches: Boolean get() = !inputChanged && recentSearches.isNotEmpty()
        val isInputTooShort: Boolean get() = !currentSearchFilter.isSearchable()
    }

    sealed class UiAction {
        data object NavigateBack : UiAction()
        data class OpenDocument(val documentId: String) : UiAction()
    }

    sealed class UiEvent {
        data class SearchInputChanged(val searchFilter: String) : UiEvent()
        data class SearchRequested(val searchFilter: String) : UiEvent()
        data class RecentSearchClicked(val term: String) : UiEvent()
        data class SearchExpandedChanged(val isSearchExpanded: Boolean) : UiEvent()

        data class SearchResultClicked(val searchResultItem: SearchResultItem) : UiEvent()
        data class SearchResultLongClicked(val searchResultItem: SearchResultItem) : UiEvent()
    }

    companion object {
        const val MIN_SEARCH_LENGTH = 2

        private fun String.isSearchable() = trim().length >= MIN_SEARCH_LENGTH
    }
}