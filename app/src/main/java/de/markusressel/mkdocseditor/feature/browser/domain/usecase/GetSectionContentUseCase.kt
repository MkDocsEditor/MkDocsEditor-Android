package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import com.dropbox.android.external.store4.StoreRequest
import com.dropbox.android.external.store4.StoreResponse
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class GetSectionContentUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    operator fun invoke(
        sectionId: String,
        refresh: Boolean = false
    ): Flow<StoreResponse<SectionEntity>> =
        dataRepository.sectionStore.stream(
            StoreRequest.cached(
                key = sectionId,
                refresh = refresh
            )
        )
}