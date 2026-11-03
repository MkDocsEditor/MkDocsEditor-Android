package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResourcePersistenceManager @Inject constructor(
    boxStore: BoxStore,
) : PersistenceManagerBase<ResourceEntity>(boxStore, ResourceEntity::class) {

    fun observeById(resourceId: String): Flow<ResourceEntity?> =
        observeFirst(ResourceEntity_.id.equal(resourceId, StringOrder.CASE_SENSITIVE))
}
