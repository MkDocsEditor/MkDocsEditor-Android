package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class OpenDocumentInBrowserUseCase @Inject constructor(
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val getDocumentUrlUseCase: GetDocumentUrlUseCase,
    private val chromeCustomTabManager: ChromeCustomTabManager,
) {
    suspend operator fun invoke(documentId: String): Boolean {
        val backendConfig = requireNotNull(getCurrentBackendConfigUseCase())
        val webBaseUri = backendConfig.mkDocsWebConfig?.domain
        if (webBaseUri.isNullOrBlank()) {
            return false
        }

        val document = getDocumentUseCase(documentId).data ?: return false

        val url = getDocumentUrlUseCase.invoke(backendConfig, document)
        return if (url != null) {
            chromeCustomTabManager.openChromeCustomTab(url)
            true
        } else {
            false
        }
    }
}