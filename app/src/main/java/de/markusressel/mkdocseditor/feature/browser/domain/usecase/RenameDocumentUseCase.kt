package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import javax.inject.Inject

internal class RenameDocumentUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(sectionId: String, documentName: String): DocumentModel {
        return restClient.renameDocument(sectionId, documentName).get()
    }
}