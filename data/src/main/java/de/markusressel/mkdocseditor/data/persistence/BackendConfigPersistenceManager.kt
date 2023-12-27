package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import io.objectbox.kotlin.query
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfigPersistenceManager @Inject constructor(
    private val backendServerConfigPersistenceManager: BackendServerConfigPersistenceManager
) : PersistenceManagerBase<BackendConfigEntity>(BackendConfigEntity::class) {

    fun selectBackendConfig(id: Long) {
        boxStore.runInTx {
            // disable all others
            standardOperation().query {
                equal(BackendConfigEntity_.isSelected, true)
            }.find().forEach {
                standardOperation().put(it.apply { isSelected = false })
            }

            // enable the given one
            val backendConfigEntity = standardOperation().query {
                equal(BackendConfigEntity_.entityId, id)
            }.findUnique()
            requireNotNull(backendConfigEntity)
            standardOperation().put(backendConfigEntity.apply { isSelected = true })
        }
    }

    fun add(toBackendConfigEntity: BackendConfigEntity): Long {
        var id = 0L
        boxStore.runInTx {
            id = standardOperation().put(toBackendConfigEntity)
        }
        return id
    }

}