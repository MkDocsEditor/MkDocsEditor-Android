package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity


data class BackendConfig(
    val id: Long = 0L,
    val name: String,
    val description: String,
    val serverConfig: BackendServerConfig,
    val authConfig: AuthConfig,
)

internal fun BackendConfigEntity.toBackendConfig(): BackendConfig {
    return BackendConfig(
        name = name,
        description = description,
        serverConfig = BackendServerConfig(
            domain = serverConfig.target.domain,
            port = serverConfig.target.port,
            useSsl = serverConfig.target.useSsl,
            webBaseUri = serverConfig.target.webBaseUri,
        ),
        authConfig = AuthConfig(
            username = authConfig.target.username,
            password = authConfig.target.password,
        )
    )
}