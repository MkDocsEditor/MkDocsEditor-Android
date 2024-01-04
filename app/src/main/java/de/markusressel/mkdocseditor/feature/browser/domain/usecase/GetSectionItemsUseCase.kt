package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import kotlinx.coroutines.flow.Flow
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetSectionItemsUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(
        sectionId: String,
    ): Flow<StoreReadResponse<SectionData>> =
//        dataRepository.sectionMutableStore.stream<SectionData>(
//            StoreReadRequest.cached(
//                key = sectionId,
//                refresh = refresh
//            )
//        )
        dataRepository.sectionStore.stream(
            StoreReadRequest.freshWithFallBackToSourceOfTruth(
                key = sectionId
            )
        )
}
