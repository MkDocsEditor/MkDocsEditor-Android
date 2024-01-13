package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.resource.ResourceModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RenameResourceUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(resourceId: String, name: String): ResourceModel {
        return restClient.renameResource(resourceId, name).get()
    }
}