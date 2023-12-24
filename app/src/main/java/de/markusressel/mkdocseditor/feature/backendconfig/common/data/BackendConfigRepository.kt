package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.BackendAuthConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.BackendConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.BackendAuthConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import de.markusressel.mkdocseditor.network.OfflineModeManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class BackendConfigRepository @Inject constructor(
    private val offlineModeManager: OfflineModeManager,
    private val backendConfigPersistenceManager: BackendConfigPersistenceManager,
    private val backendAuthConfigPersistenceManager: BackendAuthConfigPersistenceManager,
) {

    fun getBackendConfigs() = backendConfigPersistenceManager.standardOperation().all.toList()

    fun getAuthConfigs() = backendAuthConfigPersistenceManager.standardOperation().all.toList()

    fun add(config: BackendConfig) {
        backendConfigPersistenceManager.standardOperation().put(config.toBackendConfigEntity())
    }

    fun add(config: AuthConfig) {
        backendAuthConfigPersistenceManager.standardOperation()
            .put(config.toBackendAuthConfigEntity())
    }

    private fun BackendConfig.toBackendConfigEntity() = BackendConfigEntity(
        name = name,
        description = description,
    ).apply {
        serverConfig.target = this@toBackendConfigEntity.serverConfig.toBackendServerConfigEntity()
        authConfig.target = this@toBackendConfigEntity.authConfig.toBackendAuthConfigEntity()

    }

    private fun AuthConfig.toBackendAuthConfigEntity() = BackendAuthConfigEntity(
        username = username,
        password = password,
    )

    private fun BackendServerConfig.toBackendServerConfigEntity() = BackendServerConfigEntity(
        domain = domain,
        port = port,
        useSsl = useSsl,
    )

    fun get(id: Long): BackendConfigEntity? {
        return backendConfigPersistenceManager.standardOperation().get(id)
    }

}
