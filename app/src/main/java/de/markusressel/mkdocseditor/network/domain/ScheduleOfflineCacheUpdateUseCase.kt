package de.markusressel.mkdocseditor.network.domain

import android.content.Context
import androidx.work.WorkManager
import de.markusressel.mkdocseditor.service.enqueueOfflineSync
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleOfflineCacheUpdateUseCase @Inject constructor(
    private val context: Context,
) {
    /**
     * Schedules an update of the offline cache of all document files
     */
    operator fun invoke() {
        WorkManager.getInstance(context).enqueueOfflineSync()
    }
}
