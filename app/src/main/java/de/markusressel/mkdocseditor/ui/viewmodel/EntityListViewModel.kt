package de.markusressel.mkdocseditor.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Base class for implementing a ViewModel for item lists
 */
abstract class EntityListViewModel : ViewModel() {

    // TODO: use savedState
    val currentSearchFilter = MutableStateFlow("")

    // TODO: save in state
    val lastScrollPosition = MutableStateFlow(0)

    /**
     * Override this if you want to use a different page size
     */
    open fun getPageSize(): Int {
        return DEFAULT_PAGING_SIZE
    }

    companion object {
        private val DEFAULT_PAGING_SIZE = 1
    }

}