package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetDocumentUseCase @Inject constructor(
    private val dataRepository: DataRepository
) {
    /**
     * @param onCached called with what the database knows, before the server is asked
     */
    suspend operator fun invoke(
        documentId: String,
        onCached: (DocumentEntity?) -> Unit = {},
    ): Resource<DocumentEntity?> {
        // this writes the whole item tree to the database
        return dataRepository.getDocument(documentId)
            .flowOn(Dispatchers.IO)
            .onEach { if (it is Resource.Loading) onCached(it.data) }
            .first { it is Resource.Success || it is Resource.Error }
    }
}
