package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ComputePathToSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(
        sectionId: String
    ): List<SectionItem> {
        return dataRepository.getSectionsTo(sectionId).map {
            SectionItem(
                id = it.id,
                name = it.name,
            )
        }
    }
}