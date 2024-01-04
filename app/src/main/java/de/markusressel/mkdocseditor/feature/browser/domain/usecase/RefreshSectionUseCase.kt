package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import javax.inject.Inject

internal class RefreshSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(sectionId: String) {
        dataRepository.sectionStore.fresh(sectionId)
    }
}