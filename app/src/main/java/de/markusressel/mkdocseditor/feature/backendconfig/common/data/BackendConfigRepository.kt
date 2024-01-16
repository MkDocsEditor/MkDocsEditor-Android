package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.BackendAuthConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.BackendConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.BackendServerConfigPersistenceManager
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.UserPasswordAuthConfigEntity
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
    private val backendAuthConfigPersistenceManager: BackendAuthConfigPersistenceManager,
) {

    fun getBackendConfigs() = backendConfigPersistenceManager.standardOperation().all.toList()

    fun getBackendConfigsFlow(): Flow<List<BackendConfigEntity>> {
        return backendConfigPersistenceManager.standardOperation().query {
            order(BackendConfigEntity_.name)
        }.flow()
    }

    fun getAuthConfigs() = backendAuthConfigPersistenceManager.standardOperation().all.toList()

    fun addOrUpdate(config: BackendConfig): Long {
        return backendConfigPersistenceManager.add(config.toBackendConfigEntity())
    }

    fun addOrUpdate(config: AuthConfig): Long {
        return backendAuthConfigPersistenceManager.standardOperation()
            .put(config.toBackendAuthConfigEntity())
    }

    fun addOrUpdate(serverConfig: BackendServerConfig): Long {
        return backendServerConfigPersistenceManager.standardOperation()
            .put(serverConfig.toBackendServerConfigEntity())
    }

    private fun BackendConfig.toBackendConfigEntity() = BackendConfigEntity(
        entityId = id,
        name = name,
        description = description,
        isSelected = isSelected,
    ).apply {
        serverConfig.target = this@toBackendConfigEntity.serverConfig?.toBackendServerConfigEntity()
        authConfig.target = this@toBackendConfigEntity.backendAuthConfig?.toBackendAuthConfigEntity()

    }

    private fun AuthConfig.toBackendAuthConfigEntity() = UserPasswordAuthConfigEntity(
        entityId = id,
        username = username,
        password = password,
    )

    private fun BackendServerConfig.toBackendServerConfigEntity() = BackendServerConfigEntity(
        entityId = id,
        domain = domain,
        port = port,
        useSsl = useSsl,
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

    fun delete(config: BackendConfig): Boolean {
        return backendConfigPersistenceManager.standardOperation().remove(config.id)
    }

}
