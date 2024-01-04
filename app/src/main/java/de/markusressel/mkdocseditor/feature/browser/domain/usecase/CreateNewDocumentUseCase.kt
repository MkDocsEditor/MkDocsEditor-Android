package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CreateNewDocumentUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(sectionId: String, documentName: String): String {
        val name = documentName.ifEmpty { "New Document" }
        return dataRepository.createNewDocument(name, sectionId)
    }
}