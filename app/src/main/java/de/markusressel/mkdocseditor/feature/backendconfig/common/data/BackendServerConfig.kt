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

/** What websocket URLs start with, from [baseUrl] because [BackendServerConfig.domain] can hold a path and a protocol */
internal val BackendServerConfig.websocketBaseUrl: String
    get() = baseUrl.replaceFirst("http", "ws")

internal val BackendServerConfig.eventsWebsocketUrl: String
    get() = websocketBaseUrl + "events/ws/"

/**
 * A stored address as the user would write it: without the port when it is the usual one of the protocol, so
 * `ParsedBackendUrl.parse` reads the same address back.
 *
 * @param domain the host, optionally followed by a path
 */
internal fun displayAddress(domain: String, port: Int, useSsl: Boolean): String {
    if (domain.isBlank()) return ""
    val protocol = if (useSsl) "https" else "http"
    val host = domain.substringBefore('/')
    val path = domain.substringAfter('/', "").trim('/')
    val shownPort = if (port == if (useSsl) 443 else 80) "" else ":$port"
    return "$protocol://$host$shownPort" + if (path.isEmpty()) "" else "/$path"
}
