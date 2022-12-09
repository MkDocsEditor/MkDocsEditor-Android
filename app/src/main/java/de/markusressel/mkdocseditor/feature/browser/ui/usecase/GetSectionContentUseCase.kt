package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
internal class GetSectionContentUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(sectionId: Flow<String>) =
        sectionId.mapLatest { sectionId ->
            dataRepository.getSection(sectionId)
        }
}