package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.data.persistence.entity.BackendServerConfigEntity

data class BackendServerConfig(
    val id: Long = 0,
    val domain: String,
    val port: Int,
    val useSsl: Boolean,
)

internal fun BackendServerConfigEntity.toServerConfig() = BackendServerConfig(
    id = entityId,
    domain = domain,
    port = port,
    useSsl = useSsl,
)

/**
 * The base URL of the backend API, built the same way as the REST client does
 */
val BackendServerConfig.baseUrl: String
    get() {
        val protocol = if (useSsl) "https" else "http"
        val sanitized = domain.trim().removePrefix("http://").removePrefix("https://")
        val host = sanitized.substringBefore("/")
        val path = sanitized.substringAfter("/", "").trim('/')
        return "$protocol://$host:$port/" + if (path.isEmpty()) "" else "$path/"
    }
