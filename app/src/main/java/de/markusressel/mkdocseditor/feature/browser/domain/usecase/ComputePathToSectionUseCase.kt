package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ComputePathToSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(
        sectionId: String
    ): List<SectionItem> = withContext(Dispatchers.IO) {
        dataRepository.getSectionsTo(sectionId).map {
            SectionItem(
                id = it.id,
                name = it.name,
            )
        }
    }
}