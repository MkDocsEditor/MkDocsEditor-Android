package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity


data class BackendConfig(
    val id: Long = 0L,
    val name: String,
    val description: String,
    val serverConfig: BackendServerConfig,
    val authConfig: BackendAuthConfig,
)

internal fun BackendConfigEntity.toBackendConfig(): BackendConfig {
    return BackendConfig(
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