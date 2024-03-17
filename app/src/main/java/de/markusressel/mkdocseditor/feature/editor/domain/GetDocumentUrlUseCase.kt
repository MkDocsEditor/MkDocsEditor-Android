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
        val domain = mkdDocsWebConfig?.domain ?: return null
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
            "$domain/view/$pagePath"
        ).joinToString(separator = "")

        return result
    }
}