package de.markusressel.mkdocseditor.feature.browser.ui

import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem

internal data class UiState(
    val isLoading: Boolean = false,

    val isSearchExpanded: Boolean = false,
    val currentSearchFilter: String = "",
    val isSearching: Boolean = false,

    val listItems: List<IdentifiableListItem> = emptyList()
)