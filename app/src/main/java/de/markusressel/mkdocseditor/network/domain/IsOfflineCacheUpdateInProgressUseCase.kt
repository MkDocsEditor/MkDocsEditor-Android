package de.markusressel.mkdocseditor.network.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsOfflineCacheUpdateInProgressUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    /**
     * Check if an offline cache update is currently in progress
     *
     * @see DataRepository.backgroundSyncInProgress
     */
    operator fun invoke(): StateFlow<Boolean> {
        return dataRepository.backgroundSyncInProgress
    }
}