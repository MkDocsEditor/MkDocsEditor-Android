package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadResourceUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(resourceId: String): ByteArray {
        return restClient.downloadResource(resourceId).get()
    }
}