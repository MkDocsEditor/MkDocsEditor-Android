package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import de.markusressel.mkdocseditor.feature.browser.data.DataRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import org.mobilenativefoundation.store.store5.StoreReadRequest
import org.mobilenativefoundation.store.store5.StoreReadResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class RefreshSectionUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    suspend operator fun invoke(sectionId: String) {
        coroutineScope {
            dataRepository.sectionStore.stream(
                StoreReadRequest.freshWithFallBackToSourceOfTruth(
                    key = sectionId
                )
            ).collectLatest {
                when (it) {
                    is StoreReadResponse.Data -> {
                        it.throwIfError()
                        cancel()
                    }

                    is StoreReadResponse.Error.Exception -> it.throwIfError()
                    is StoreReadResponse.Error.Message -> it.throwIfError()
                    is StoreReadResponse.Loading -> {}
                    is StoreReadResponse.NoNewData -> {
                        it.throwIfError()
                        cancel()
                    }
                }
            }
        }
    }
}