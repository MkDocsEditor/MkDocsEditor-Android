package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class CreateNewSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(sectionName: String, sectionId: String) {
        dataRepository.createNewSection(sectionName, sectionId)
    }
}