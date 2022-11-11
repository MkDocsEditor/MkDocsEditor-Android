package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import de.markusressel.mkdocseditor.data.DataRepository
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.util.Resource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
internal class GetSectionContentUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    @OptIn(FlowPreview::class)
    operator fun invoke(sectionId: Flow<String>): Flow<Resource<SectionEntity?>> {
        return sectionId.mapLatest { sectionId ->
            dataRepository.getSection(sectionId)
        }.flattenConcat()
    }
}