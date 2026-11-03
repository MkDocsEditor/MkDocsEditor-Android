package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.ajalt.timberkt.Timber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.CancellationException

internal const val OFFLINE_SYNC_WORK_NAME = "update-offline-cache"

/** after this many runs a failed sync waits for the next start of the app */
internal const val MAX_OFFLINE_SYNC_ATTEMPTS = 3

/**
 * Starts the [OfflineSyncWorker], unless it is already waiting or running: a second one would fetch the same documents.
 * WorkManager runs it once there is a network, and keeps it across restarts of the app.
 */
internal fun WorkManager.enqueueOfflineSync() {
    val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    enqueueUniqueWork(OFFLINE_SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
}

/** What a run of the [OfflineSyncWorker] tells WorkManager. It only tries again when that can help. */
internal fun CacheOutcome.toWorkResult(attempt: Int): Result = when (this) {
    CacheOutcome.Complete, CacheOutcome.NotSignedIn -> Result.success()
    CacheOutcome.SomeFailed -> if (attempt + 1 < MAX_OFFLINE_SYNC_ATTEMPTS) Result.retry() else Result.success()
}

@HiltWorker
internal class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataRepository: DataRepository,
    private val restClient: IMkDocsRestClient,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager,
    private val preferencesHolder: KutePreferencesHolder,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        dataRepository.setBackgroundSyncInProgress(true)
        return try {
            work().toWorkResult(runAttemptCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e) { "Offline cache sync failed" }
            CacheOutcome.SomeFailed.toWorkResult(runAttemptCount)
        } finally {
            dataRepository.setBackgroundSyncInProgress(false)
        }
    }

    private suspend fun work(): CacheOutcome {
        val documentIds = dataRepository.getAllDocuments().map { it.id }
        Timber.d { "Running background worker for updating offline cache of ${documentIds.size} documents..." }
        // use random order to evenly distribute probability of caching a document
        val outcome = cacheDocuments(
            documentIds = documentIds.shuffled(),
            getContent = restClient::getDocumentContent,
            store = { documentId, text ->
                documentContentPersistenceManager.insertOrUpdate(documentId = documentId, text = text)
            },
        )
        if (outcome == CacheOutcome.Complete) {
            Timber.d { "Offline cache sync job complete." }
            preferencesHolder.lastOfflineCacheUpdate.updateToNow()
        }
        return outcome
    }
}
