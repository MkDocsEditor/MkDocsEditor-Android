package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.util.Resource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDocumentUseCase @Inject constructor(
    private val dataRepository: DataRepository
) {
    suspend operator fun invoke(documentId: String): Flow<Resource<DocumentEntity?>> {
        return dataRepository.getDocument(documentId)
    }
}