package de.markusressel.mkdocseditor.feature.editor.domain

import androidx.core.text.htmlEncode
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OpenDocumentInBrowserUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val chromeCustomTabManager: ChromeCustomTabManager,
) {
    suspend operator fun invoke(documentId: String): Boolean {
        val backendConfig = requireNotNull(getCurrentBackendConfigUseCase())
        val webBaseUri = backendConfig.mkDocsWebConfig?.domain
        if (webBaseUri.isNullOrBlank()) {
            return false
        }

        val document = getDocumentUseCase(documentId).data ?: return false

        val url = computeDocumentUrl(backendConfig, document)
        return if (url != null) {
            chromeCustomTabManager.openChromeCustomTab(url)
            true
        } else {
            false
        }
    }

    private fun computeDocumentUrl(
        backendConfig: BackendConfig,
        document: DocumentEntity
    ): String? {
        val mkdDocsWebConfig = backendConfig.mkDocsWebConfig
        val domain = mkdDocsWebConfig?.domain ?: return null
        val protocol = when (mkdDocsWebConfig.useSsl) {
            true -> "https"
            else -> "http"
        }

        val authConfig = backendConfig.mkDocsWebAuthConfig
        val username = authConfig?.username
        val password = authConfig?.password
        val basicAuthInUrl = "${username?.htmlEncode()}:${password?.htmlEncode()}"

        val pagePath = when (document.url) {
            "index/" -> ""
            else -> document.url
            // this value is already url encoded
        }

        val result = listOfNotNull(
            "$protocol://",
            "$basicAuthInUrl@".takeUnless { username.isNullOrBlank() || password.isNullOrBlank() },
            "$domain/view/$pagePath"
        ).joinToString(separator = "")

        return result
    }
}