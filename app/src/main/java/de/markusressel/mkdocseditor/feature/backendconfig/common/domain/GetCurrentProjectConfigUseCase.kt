package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetCurrentProjectConfigUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient
) {
    suspend operator fun invoke() = restClient.getMkDocsConfig()
}

