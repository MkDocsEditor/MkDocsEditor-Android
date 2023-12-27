package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.BackendAuthConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.BackendConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.BackendAuthConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import de.markusressel.mkdocseditor.network.OfflineModeManager
import io.objectbox.kotlin.query
import io.objectbox.kotlin.toFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackendConfigRepository @Inject constructor(
    private val offlineModeManager: OfflineModeManager,
    private val backendConfigPersistenceManager: BackendConfigPersistenceManager,
    private val backendAuthConfigPersistenceManager: BackendAuthConfigPersistenceManager,
) {

    fun getBackendConfigs() = backendConfigPersistenceManager.standardOperation().all.toList()

    fun getBackendConfigsFlow(): Flow<List<BackendConfigEntity>> {
        return backendConfigPersistenceManager.standardOperation().query {
            order(BackendConfigEntity_.name)
        }.subscribe().toFlow().map { it.toList() }
    }

    fun getAuthConfigs() = backendAuthConfigPersistenceManager.standardOperation().all.toList()

    fun add(config: BackendConfig): Long {
        return backendConfigPersistenceManager.add(config.toBackendConfigEntity())
    }

    fun add(config: AuthConfig): Long {
        return backendAuthConfigPersistenceManager.standardOperation()
            .put(config.toBackendAuthConfigEntity())
    }

    private fun BackendConfig.toBackendConfigEntity() = BackendConfigEntity(
        entityId = id,
        name = name,
        description = description,
        isSelected = isSelected,
    ).apply {
        serverConfig.target = this@toBackendConfigEntity.serverConfig.toBackendServerConfigEntity()
        authConfig.target = this@toBackendConfigEntity.authConfig.toBackendAuthConfigEntity()

    }

    private fun AuthConfig.toBackendAuthConfigEntity() = BackendAuthConfigEntity(
        entityId = id,
        username = username,
        password = password,
    )

    private fun BackendServerConfig.toBackendServerConfigEntity() = BackendServerConfigEntity(
        entityId = id,
        domain = domain,
        port = port,
        useSsl = useSsl,
        webBaseUri = webBaseUri,
    )

    fun getBackendConfig(id: Long): BackendConfigEntity? {
        return backendConfigPersistenceManager.standardOperation().get(id)
    }

    fun deleteAuthConfig(id: Long): Boolean {
        return backendAuthConfigPersistenceManager.standardOperation().remove(id)
    }

    fun selectBackendConfig(config: BackendConfig) {
        backendConfigPersistenceManager.selectBackendConfig(config.id)
    }

}
