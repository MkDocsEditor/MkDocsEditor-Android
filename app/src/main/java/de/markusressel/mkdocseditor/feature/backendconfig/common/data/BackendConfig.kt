package de.markusressel.mkdocseditor.feature.backendconfig.common.data

data class BackendAuthConfig(
    val username: String,
    val password: String,
)

data class BackendServerConfig(
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
)

data class BackendConfig(
    val id: String,
    val name: String,
    val description: String,
    val serverConfig: BackendServerConfig,
    val authConfig: BackendAuthConfig,
)