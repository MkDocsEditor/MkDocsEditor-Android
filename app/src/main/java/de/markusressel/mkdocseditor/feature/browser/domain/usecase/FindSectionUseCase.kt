package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import org.mobilenativefoundation.store.store5.impl.extensions.get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(sectionId: String): SectionData {
        return dataRepository.sectionStore.get(sectionId)
    }
}