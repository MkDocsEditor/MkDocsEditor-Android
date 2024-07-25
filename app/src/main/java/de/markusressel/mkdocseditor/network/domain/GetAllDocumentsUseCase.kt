package de.markusressel.mkdocseditor.network.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAllDocumentsUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(): List<DocumentData> {
        return dataRepository.getAllDocuments()
    }
}