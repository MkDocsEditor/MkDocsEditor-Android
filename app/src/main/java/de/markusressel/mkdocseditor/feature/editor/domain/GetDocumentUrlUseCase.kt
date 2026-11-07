package de.markusressel.mkdocseditor.feature.editor.domain

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
        val domain = mkdDocsWebConfig?.domain?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return null
        val protocol = when (mkdDocsWebConfig.useSsl) {
            true -> "https"
            else -> "http"
        }

        val pagePath = when (entity.url) {
            "index/" -> ""
            else -> entity.url
            // this value is already url encoded
        }

        // no credentials: the wiki accepts the session of the web client and sends others to its login
        return "$protocol://${hostWithPort(domain, mkdDocsWebConfig.port, mkdDocsWebConfig.useSsl)}${sitePath(domain)}$pagePath"
    }
}

/**
 * What comes after the stored [domain] before the page. A wiki URL from discovery holds the path of the site, like
 * `wiki.example.com/view`; one typed by hand in the old form is only the host, and the site was under `/view/`.
 */
private fun sitePath(domain: String): String = if ('/' in domain) "/" else "/view/"

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
