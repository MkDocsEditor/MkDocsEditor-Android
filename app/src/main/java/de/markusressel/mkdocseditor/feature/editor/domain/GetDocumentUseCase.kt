package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.util.Resource
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDocumentUseCase @Inject constructor(
    private val dataRepository: DataRepository
) {
    suspend operator fun invoke(documentId: String): Resource<DocumentEntity?> {
        return dataRepository.getDocument(documentId)
            .first { it is Resource.Success || it is Resource.Error }
    }
}
