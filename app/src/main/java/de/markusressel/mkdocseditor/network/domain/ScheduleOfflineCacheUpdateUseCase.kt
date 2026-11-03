package de.markusressel.mkdocseditor.network.domain

import android.content.Context
import androidx.work.WorkManager
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.service.enqueueOfflineSync
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleOfflineCacheUpdateUseCase @Inject constructor(
    private val context: Context,
    private val getOfflineModeEnabledSettingFlowUseCase: GetOfflineModeEnabledSettingFlowUseCase,
) {
    /**
     * Schedules an update of the offline cache of all document files
     */
    operator fun invoke(evenInOfflineMode: Boolean = false) {
        if (getOfflineModeEnabledSettingFlowUseCase().value && !evenInOfflineMode) {
            Timber.d { "Offline mode is active, no offline cache update is scheduled." }
            return
        }

        WorkManager.getInstance(context).enqueueOfflineSync()
    }
}
