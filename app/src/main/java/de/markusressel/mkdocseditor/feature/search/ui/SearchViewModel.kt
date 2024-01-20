package de.markusressel.mkdocseditor.feature.search.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import kotlinx.coroutines.launch
import javax.inject.Inject

internal class SearchViewModel @Inject constructor(

) : ViewModel() {

    fun onUiEvent(uiEvent: UiEvent) {
        viewModelScope.launch {
            when (uiEvent) {
                is UiEvent.SearchInputChanged -> {

                }
            }
        }
    }

    data class UiState(
        val currentSearchFilter: String = "",
        val isSearchExpanded: Boolean = false,
        val currentSearchResults: List<SearchResultItem> = emptyList(),
    )

    sealed class UiEvent {
        data class SearchInputChanged(val searchFilter: String) : UiEvent()
    }

}