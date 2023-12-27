package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity

data class BackendServerConfig(
    val id: Long = 0,
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
    val webBaseUri: String,
)

internal fun BackendServerConfigEntity.toServerConfig() = BackendServerConfig(
    id = entityId,
    domain = domain,
    port = port,
    useSsl = useSsl,
    webBaseUri = webBaseUri,
)
