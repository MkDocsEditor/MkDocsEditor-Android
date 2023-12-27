package de.markusressel.mkdocseditor.feature.backendconfig.common.data

data class BackendServerConfig(
    val id: Long = 0,
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
    val webBaseUri: String,
)