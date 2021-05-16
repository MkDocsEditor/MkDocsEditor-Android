package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.ajalt.timberkt.Timber
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import kotlinx.coroutines.coroutineScope


@HiltWorker
class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val restClient: MkDocsRestClient,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val documentIds = inputData.getStringArray(DOCUMENT_IDS_KEY)
        if (documentIds == null) {
            Timber.wtf { "Missing documentIds parameter!" }
            return@coroutineScope Result.failure()
        }

        Timber.d { "Scheduling background thread for updating offline cache of ${documentIds.size} documents..." }
        // use random order to evenly distribute probability of caching a document
        documentIds.toSet().shuffled().forEach { documentId ->
            restClient.getDocumentContent(documentId).fold(success = { text ->
                documentContentPersistenceManager.insertOrUpdate(
                    documentId = documentId,
                    text = text
                )
                Timber.d { "Offline cache sync finished successfully for documentId: $documentId" }
            }, failure = {
                Timber.e(it) { "Offline cache sync error for documentId: $documentId (rescheduling)" }
            })
        }

        Timber.d { "Offline cache sync job complete." }
        Result.success()
    }

    companion object {
        const val DOCUMENT_IDS_KEY = "DOCUMENT_IDS_KEY"
    }
}