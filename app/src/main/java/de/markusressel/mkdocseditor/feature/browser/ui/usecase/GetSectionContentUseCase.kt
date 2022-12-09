package de.markusressel.mkdocseditor.feature.browser.ui.usecase

import com.dropbox.android.external.store4.StoreRequest
import com.dropbox.android.external.store4.StoreResponse
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class GetSectionContentUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(sectionId: Flow<String>, refresh: Boolean = false): Flow<StoreResponse<SectionEntity>> =
        sectionId.mapLatest { sectionId ->
//            dataRepository.getSection(sectionId)
            dataRepository.sectionStore.stream(StoreRequest.cached(key = sectionId, refresh = refresh))
        }.flattenConcat()
}