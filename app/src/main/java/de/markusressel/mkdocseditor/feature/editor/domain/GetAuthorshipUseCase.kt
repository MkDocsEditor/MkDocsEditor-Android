package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject

internal class GetAuthorshipUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(documentId: String) = restClient.getAuthorship(documentId)
}
