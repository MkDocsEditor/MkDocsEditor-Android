package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import com.dropbox.android.external.store4.fresh
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import javax.inject.Inject

internal class RefreshSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(sectionId: String) {
        dataRepository.sectionStore.fresh(sectionId)
    }
}