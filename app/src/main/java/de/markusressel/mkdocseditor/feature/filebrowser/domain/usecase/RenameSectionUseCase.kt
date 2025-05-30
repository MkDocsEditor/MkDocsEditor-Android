package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.section.SectionModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RenameSectionUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(sectionId: String, sectionName: String): SectionModel {
        return restClient.renameSection(sectionId, sectionName).get()
    }
}