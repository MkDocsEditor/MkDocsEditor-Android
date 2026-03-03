package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfigPersistenceManager @Inject constructor(
    private val backendAuthConfigPersistenceManager: BackendAuthConfigPersistenceManager,
    private val backendServerConfigPersistenceManager: BackendServerConfigPersistenceManager,
    private val mkdDocsWebConfigPersistenceManager: MkdDocsWebConfigPersistenceManager,
) : PersistenceManagerBase<BackendConfigEntity>(BackendConfigEntity::class) {

    fun selectBackendConfig(id: Long) {
        boxStore.runInTx {
            // disable all others
            standardOperation().query(BackendConfigEntity_.isSelected.equal(true)).build().find().forEach {
                standardOperation().put(it.apply { isSelected = false })
            }

            // enable the given one
            val backendConfigEntity =
                standardOperation().query(BackendConfigEntity_.entityId.equal(id)).build().findUnique()
            requireNotNull(backendConfigEntity)
            standardOperation().put(backendConfigEntity.apply { isSelected = true })
        }
    }

    fun add(entity: BackendConfigEntity): Long {
        var id = 0L
        boxStore.runInTx {
            entity.authConfig.target?.let { backendAuthConfigPersistenceManager.standardOperation().put(it) }
            backendServerConfigPersistenceManager.standardOperation().put(entity.serverConfig.target)
            entity.mkDocsWebConfig.target?.let { mkdDocsWebConfigPersistenceManager.standardOperation().put(it) }
            entity.mkDocsWebAuthConfig.target?.let { backendAuthConfigPersistenceManager.standardOperation().put(it) }
            id = standardOperation().put(entity)
        }
        return id
    }

}