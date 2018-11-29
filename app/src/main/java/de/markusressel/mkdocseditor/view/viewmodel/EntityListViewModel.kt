package de.markusressel.mkdocseditor.view.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.paging.LivePagedListBuilder
import androidx.paging.PagedList
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import io.objectbox.android.ObjectBoxDataSource
import io.objectbox.query.Query

/**
 * Base class for implementing a ViewModel for item lists
 */
abstract class EntityListViewModel<EntityType : Any> : ViewModel() {

    private var listLiveData: LiveData<PagedList<EntityType>>? = null

    /**
     * Get the LiveData object for this EntityListViewModel
     */
    fun getListLiveData(persistenceManager: PersistenceManagerBase<EntityType>): LiveData<PagedList<EntityType>> {
        if (listLiveData == null) {
            listLiveData = LivePagedListBuilder(ObjectBoxDataSource.Factory(createDbQuery(persistenceManager)), getPageSize()).build()
        }

        return listLiveData!!
    }

    /**
     * Define the query to use for the list data of this ViewModel
     */
    abstract fun createDbQuery(persistenceManager: PersistenceManagerBase<EntityType>): Query<EntityType>

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