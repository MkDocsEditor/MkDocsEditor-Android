package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DeleteResourceUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(resourceId: String): String {
        return restClient.deleteResource(resourceId).get()
    }
}