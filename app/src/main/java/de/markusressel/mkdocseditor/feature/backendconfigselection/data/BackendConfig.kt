package de.markusressel.mkdocseditor.feature.backendconfigselection.data

data class BackendServerConfig(
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
    val username: String,
    val password: String,
)

data class BackendConfig(
    val id: String,
    val name: String,
    val description: String,
    val serverConfiguration: BackendServerConfig,
)