package de.markusressel.mkdocseditor.ui.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * Base class for implementing a ViewModel for item lists
 */
abstract class EntityListViewModel : ViewModel() {

    // TODO: use savedState
    val currentSearchFilter = MutableLiveData<String>()

    // TODO: save in state
    val lastScrollPosition = MutableLiveData(0)

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