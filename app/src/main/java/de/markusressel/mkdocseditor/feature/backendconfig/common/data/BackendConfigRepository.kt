package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocseditor.data.persistence.BackendConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.BackendServerConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.MkdDocsWebConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.MkDocsWebConfigEntity
import io.objectbox.kotlin.flow
import io.objectbox.kotlin.query
import io.objectbox.kotlin.toFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackendConfigRepository @Inject constructor(
    private val backendConfigPersistenceManager: BackendConfigPersistenceManager,
    private val backendServerConfigPersistenceManager: BackendServerConfigPersistenceManager,
    private val mkdDocsWebConfigPersistenceManager: MkdDocsWebConfigPersistenceManager,
    private val wikiScope: WikiScope,
) {

    fun getBackendConfigs() = backendConfigPersistenceManager.standardOperation().all.toList()

    fun getBackendConfigsFlow(): Flow<List<BackendConfigEntity>> {
        return backendConfigPersistenceManager.standardOperation()
            .query()
            .order(BackendConfigEntity_.name)
            .build()
            .flow()
    }

    fun addOrUpdate(config: BackendConfig): Long {
        return backendConfigPersistenceManager.add(config.toBackendConfigEntity())
    }

    fun addOrUpdate(serverConfig: BackendServerConfig): Long {
        return backendServerConfigPersistenceManager.standardOperation()
            .put(serverConfig.toBackendServerConfigEntity())
    }

    fun addOrUpdate(mkDocsWebConfig: MkDocsWebConfig): Long {
        return mkdDocsWebConfigPersistenceManager.standardOperation()
            .put(mkDocsWebConfig.toMkDocsWebConfigEntity())
    }

    private fun BackendConfig.toBackendConfigEntity() = BackendConfigEntity(
        entityId = id,
        name = name,
        description = description,
        isSelected = isSelected,
    ).apply {
        serverConfig.target = this@toBackendConfigEntity.serverConfig?.toBackendServerConfigEntity()
        mkDocsWebConfig.target =
            this@toBackendConfigEntity.mkDocsWebConfig?.toMkDocsWebConfigEntity()
    }

    private fun BackendServerConfig.toBackendServerConfigEntity() = BackendServerConfigEntity(
        entityId = id,
        domain = domain,
        port = port,
        useSsl = useSsl,
    )

    private fun MkDocsWebConfig.toMkDocsWebConfigEntity() = MkDocsWebConfigEntity(
        entityId = id,
        domain = domain,
        port = port,
        useSsl = useSsl,
    )

    fun getBackendConfig(id: Long): BackendConfigEntity? {
        return backendConfigPersistenceManager.standardOperation().get(id)
    }

    fun selectBackendConfig(config: BackendConfig) {
        backendConfigPersistenceManager.selectBackendConfig(config.id)
        // before anything reads wiki data of the new server
        wikiScope.refresh()
    }

    fun selectedBackendConfig(): BackendConfigEntity? {
        return backendConfigPersistenceManager.standardOperation().query {
            equal(BackendConfigEntity_.isSelected, true)
        }.findUnique()
    }

    fun selectedBackendConfigFlow(): Flow<BackendConfigEntity?> {
        return backendConfigPersistenceManager.standardOperation().query {
            equal(BackendConfigEntity_.isSelected, true)
        }.subscribe().toFlow().map { it.firstOrNull() }
    }

    /**
     * Removes all stored passwords, see [MigrateBackendBasicAuthUseCase].
     *
     * @param onRemoved called with the backend config id and the username of the removed credentials
     */
    fun removeStoredPasswords(onRemoved: (backendId: Long, username: String) -> Unit) =
        backendConfigPersistenceManager.removeStoredPasswords(onRemoved)

    fun unsyncedPages(config: BackendConfig): Int = wikiScope.unsyncedPages(config.id, config.origin())

    private fun BackendConfig.origin() = serverConfig?.let { AutomergeDocumentSyncManager.originOf(it.domain, it.port) }

    fun delete(config: BackendConfig): Boolean {
        wikiScope.forget(config.id, config.origin())
        return backendConfigPersistenceManager.standardOperation().remove(config.id)
    }

}
