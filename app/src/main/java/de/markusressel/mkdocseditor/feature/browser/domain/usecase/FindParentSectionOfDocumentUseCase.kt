package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataFactory
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindParentSectionOfDocumentUseCase @Inject constructor(
    private val dataFactory: DataFactory,
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(documentId: String): SectionData? {
        return dataRepository.getDocument(documentId).first().data?.parentSection?.target?.let {
            dataFactory.toSectionData(it)
        }
    }
}