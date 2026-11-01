package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.ajalt.timberkt.Timber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.IMkDocsRestClient


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
        return try {
            dataRepository._backgroundSyncInProgress.value = true
            work()
        } catch (e: Exception) {
            Result.failure()
        } finally {
            dataRepository._backgroundSyncInProgress.value = false
        }
    }

    private suspend fun work(): Result {
        val documentIds = inputData.getStringArray(DOCUMENT_IDS_KEY)
        if (documentIds == null) {
            Timber.wtf { "Missing documentIds parameter!" }
            return Result.failure()
        }

        Timber.d { "Running background worker for updating offline cache of ${documentIds.size} documents..." }
        // use random order to evenly distribute probability of caching a document
        val completed = cacheDocuments(
            documentIds = documentIds.toSet().shuffled(),
            getContent = restClient::getDocumentContent,
            store = { documentId, text ->
                documentContentPersistenceManager.insertOrUpdate(documentId = documentId, text = text)
            },
        )
        if (completed) {
            Timber.d { "Offline cache sync job complete." }
            preferencesHolder.lastOfflineCacheUpdate.updateToNow()
        }
        return Result.success()
    }

    companion object {
        const val DOCUMENT_IDS_KEY = "DOCUMENT_IDS_KEY"
    }
}