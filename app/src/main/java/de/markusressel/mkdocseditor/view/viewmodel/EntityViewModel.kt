package de.markusressel.mkdocseditor.view.viewmodel

import androidx.lifecycle.ViewModel
import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import io.objectbox.android.ObjectBoxLiveData
import io.objectbox.query.Query

/**
 * Base class for implementing a ViewModel for single items
 */
abstract class EntityViewModel<EntityType : Any> : ViewModel() {

    private var entityLiveData: ObjectBoxLiveData<EntityType>? = null

    /**
     * Get the LiveData object for this EntityViewModel
     */
    fun getEntityLiveData(
        persistenceManager: PersistenceManagerBase<EntityType>,
        entityId: Long
    ): ObjectBoxLiveData<EntityType> {
        if (entityLiveData == null) {
            entityLiveData = ObjectBoxLiveData(createDbQuery(persistenceManager, entityId))
        }

        return entityLiveData!!
    }

    /**
     * Define the query to use for this ViewModel
     *
     * @param persistenceManager the persistence to get the data from
     * @param entityId the entity id
     */
    abstract fun createDbQuery(
        persistenceManager: PersistenceManagerBase<EntityType>,
        entityId: Long
    ): Query<EntityType>

}