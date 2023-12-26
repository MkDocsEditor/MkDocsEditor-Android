package de.markusressel.mkdocseditor.feature.backendconfig.common.data

data class BackendServerConfig(
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
    val webBaseUri: String,
)