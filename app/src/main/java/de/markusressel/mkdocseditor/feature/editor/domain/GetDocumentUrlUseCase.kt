package de.markusressel.mkdocseditor.feature.editor.domain

import androidx.core.text.htmlEncode
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetDocumentUrlUseCase @Inject constructor(
) {
    suspend operator fun invoke(
        backendConfig: BackendConfig,
        entity: DocumentEntity,
    ): String? {
        val mkdDocsWebConfig = backendConfig.mkDocsWebConfig
        val domain = mkdDocsWebConfig?.domain?.takeIf { it.isNotBlank() } ?: return null
        val protocol = when (mkdDocsWebConfig.useSsl) {
            true -> "https"
            else -> "http"
        }

        val authConfig = backendConfig.mkDocsWebAuthConfig
        val username = authConfig?.username
        val password = authConfig?.password
        val basicAuthInUrl = "${username?.htmlEncode()}:${password?.htmlEncode()}"

        val pagePath = when (entity.url) {
            "index/" -> ""
            else -> entity.url
            // this value is already url encoded
        }

        val result = listOfNotNull(
            "$protocol://",
            "$basicAuthInUrl@".takeUnless { username.isNullOrBlank() || password.isNullOrBlank() },
            "${hostWithPort(domain, mkdDocsWebConfig.port, mkdDocsWebConfig.useSsl)}/view/$pagePath"
        ).joinToString(separator = "")

        return result
    }
}

/**
 * The old form stored port 80 for an empty field, also with https, and the port had no effect then. So https on 80
 * means no port.
 */
internal fun hostWithPort(domain: String, port: Int, useSsl: Boolean): String {
    val usual = if (useSsl) port == 443 || port == 80 else port == 80
    if (usual) return domain
    val host = domain.substringBefore('/')
    val path = domain.substringAfter('/', "")
    return "$host:$port" + if (path.isEmpty()) "" else "/$path"
}
