package de.markusressel.mkdocseditor.view.viewmodel

import androidx.lifecycle.ViewModel

/**
 * Base class for implementing a ViewModel for item lists
 */
abstract class EntityListViewModel : ViewModel() {

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