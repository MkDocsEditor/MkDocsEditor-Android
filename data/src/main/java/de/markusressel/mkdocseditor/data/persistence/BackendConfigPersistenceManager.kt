package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import io.objectbox.BoxStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackendConfigPersistenceManager @Inject constructor(
    boxStore: BoxStore,
    private val backendAuthConfigPersistenceManager: BackendAuthConfigPersistenceManager,
    private val backendServerConfigPersistenceManager: BackendServerConfigPersistenceManager,
    private val mkdDocsWebConfigPersistenceManager: MkdDocsWebConfigPersistenceManager,
    private val sectionPersistenceManager: SectionPersistenceManager,
) : PersistenceManagerBase<BackendConfigEntity>(boxStore, BackendConfigEntity::class) {

    /** Selects the given config. The stored tree is the one of the selected server, so a different server drops it. */
    fun selectBackendConfig(id: Long) {
        boxStore.runInTx {
            // disable all others
            val selected = standardOperation().query(BackendConfigEntity_.isSelected.equal(true)).build().use { it.find() }
            selected.forEach {
                standardOperation().put(it.apply { isSelected = false })
            }
            if (selected.none { it.entityId == id }) sectionPersistenceManager.deleteTreeAndContent()

            // enable the given one
            val backendConfigEntity = requireNotNull(standardOperation().get(id))
            standardOperation().put(backendConfigEntity.apply { isSelected = true })
        }
    }

    /** @return whether the config existed. Removing the selected server also drops its stored tree. */
    fun remove(id: Long): Boolean {
        var removed = false
        boxStore.runInTx {
            if (standardOperation().get(id)?.isSelected == true) sectionPersistenceManager.deleteTreeAndContent()
            removed = standardOperation().remove(id)
        }
        return removed
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