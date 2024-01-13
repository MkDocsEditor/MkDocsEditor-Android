package de.markusressel.mkdocseditor.network.domain

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.service.OfflineSyncWorker
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduleOfflineCacheUpdateUseCase @Inject constructor(
    private val context: Context,
    private val getOfflineModeEnabledSettingFlowUseCase: GetOfflineModeEnabledSettingFlowUseCase,
    private val getAllDocumentsUseCase: GetAllDocumentsUseCase,
) {
    /**
     * Schedules an update of the offline cache of all document files
     *
     * @param documentIds optionally specify a list of document id's to update
     */
    suspend operator fun invoke(
        documentIds: Collection<String>? = null,
        evenInOfflineMode: Boolean = false
    ) {
        if (getOfflineModeEnabledSettingFlowUseCase().value && !evenInOfflineMode) {
            Timber.d { "Offline mode is active, no offline cache update is scheduled." }
            return
        }

        val documentsToUpdate = documentIds
            ?: getAllDocumentsUseCase().map { it.id }
        if (documentsToUpdate.isEmpty()) {
            Timber.d { "No documentIds provided, nothing to schedule." }
            return
        }

        val workerData: Data = workDataOf(
            OfflineSyncWorker.DOCUMENT_IDS_KEY to documentsToUpdate.toTypedArray()
        )

        val offlineSyncWorker = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setInputData(workerData)
            .build()
        WorkManager.getInstance(context).enqueue(offlineSyncWorker)
    }
}