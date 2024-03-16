package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import de.markusressel.mkdocseditor.feature.browser.ui.FileBrowserViewModel
import org.mobilenativefoundation.store.store5.impl.extensions.get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FindDocumentUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(documentId: String): SectionData {
        return dataRepository.sectionStore.get(FileBrowserViewModel.ROOT_SECTION_ID)
    }
}