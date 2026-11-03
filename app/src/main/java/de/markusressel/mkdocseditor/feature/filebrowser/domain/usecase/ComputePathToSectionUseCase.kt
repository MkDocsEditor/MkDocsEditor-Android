package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ComputePathToSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    /** @return the sections from the root down to the given one, empty if it does not exist */
    suspend operator fun invoke(sectionId: String): List<SectionItem> = withContext(Dispatchers.IO) {
        dataRepository.getPathTo(sectionId)
    }
}
