package de.markusressel.mkdocseditor.data.persistence.base

import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.toFlow
import io.objectbox.query.QueryCondition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

/**
 * Base class of a persistance manager for a specific type
 */
open class PersistenceManagerBase<EntityType : Any>(
    protected val boxStore: BoxStore,
    private val entityType: KClass<EntityType>,
) {

    private val box: Box<EntityType> by lazy {
        boxStore.boxFor(entityType)
    }

    /**
     * Returns the BoxStore for this PersistenceManager to perform standard (ObjectBox) operations
     */
    fun standardOperation(): Box<EntityType> {
        return box
    }

    /** @return the only entity that matches, null if there is none */
    fun findUnique(condition: QueryCondition<EntityType>): EntityType? =
        box.query(condition).build().use { it.findUnique() }

    /** @return the database ids of all entities that match */
    fun findIds(condition: QueryCondition<EntityType>): LongArray =
        box.query(condition).build().use { it.findIds() }

    /** @return the first entity that matches, with a new value whenever an entity of this type changes */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeFirst(condition: QueryCondition<EntityType>): Flow<EntityType?> =
        box.query(condition).build().subscribe().toFlow().map { it.firstOrNull() }

    fun deleteAll() {
        box.removeAll()
    }
}
