package de.markusressel.mkdocseditor.feature.backendconfig.edit.domain

import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendAuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import javax.inject.Inject

internal class AddBackendConfigItemsUseCase @Inject constructor(
    private val backendConfigRepository: BackendConfigRepository
) {
    suspend operator fun invoke(): List<BackendConfig> {
        return backendConfigRepository.getAll().map {
            it.toBackendConfig()
        } + listOf(
            BackendConfig(
                id = "1",
                name = "Test",
                description = "Test",
                serverConfig = BackendServerConfig(
                    domain = "test",
                    port = 1234,
                    useSsl = false,
                ),
                authConfig = BackendAuthConfig(
                    username = "test",
                    password = "test",
                )
            ),
            BackendConfig(
                id = "2",
                name = "Test2",
                description = "Test2",
                serverConfig = BackendServerConfig(
                    domain = "test2",
                    port = 1234,
                    useSsl = false,
                ),
                authConfig = BackendAuthConfig(
                    username = "test2",
                    password = "test2",
                )
            ),
        )
    }

    private fun BackendConfigEntity.toBackendConfig(): BackendConfig {
        return BackendConfig(
            id = id,
            name = name,
            description = description,
            serverConfig = BackendServerConfig(
                domain = serverConfig.target.domain,
                port = serverConfig.target.port,
                useSsl = serverConfig.target.useSsl,
            ),
            authConfig = BackendAuthConfig(
                username = authConfig.target.username,
                password = authConfig.target.password,
            )
        )
    }
}

