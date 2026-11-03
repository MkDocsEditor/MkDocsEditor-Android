package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import kotlinx.coroutines.flow.Flow
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class GetSectionItemsUseCase @Inject constructor(
    private val dataRepository: DataRepository,
    private val isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
) {
    /** What is in the cache comes first, so a folder opens at once. The server only follows outside of offline mode. */
    operator fun invoke(sectionId: String): Flow<StoreReadResponse<SectionData>> =
        dataRepository.sectionStore.stream(
            StoreReadRequest.cached(
                key = sectionId,
                refresh = isOfflineModeEnabledFlowUseCase().value.not(),
            )
        )
}
