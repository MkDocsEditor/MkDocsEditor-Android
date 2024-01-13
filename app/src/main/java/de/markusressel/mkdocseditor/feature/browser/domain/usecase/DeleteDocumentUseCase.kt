package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DeleteDocumentUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(documentId: String): String {
        return restClient.deleteDocument(documentId).get()
    }
}