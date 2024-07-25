package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.document.DocumentModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RenameDocumentUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(documentId: String, documentName: String): DocumentModel {
        return restClient.renameDocument(documentId, documentName).get()
    }
}