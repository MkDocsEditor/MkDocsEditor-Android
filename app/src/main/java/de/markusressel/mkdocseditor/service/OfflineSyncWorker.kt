package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class OfflineSyncWorker(appContext: Context, workerParams: WorkerParameters)
    : Worker(appContext, workerParams) {

    @Inject
    internal lateinit var restClient: MkDocsRestClient

    @Inject
    internal lateinit var documentContentPersistenceManager: DocumentContentPersistenceManager

    override fun doWork(): Result {
        val documentIds = inputData.getStringArray(DOCUMENT_IDS_KEY)
        if (documentIds == null) {
            Timber.wtf { "Missing documentIds parameter!" }
            return Result.failure()
        }

        Timber.d { "Scheduling background thread for updating offline cache of ${documentIds.size} documents..." }
        // use random order to evenly distribute probability of caching a document
        documentIds.toSet().shuffled().forEach { documentId ->
            restClient.getDocumentContent(documentId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeBy(onSuccess = { text ->
                        documentContentPersistenceManager.insertOrUpdate(documentId = documentId, text = text)
                        Timber.d { "Offline cache sync finished successfully for documentId: $documentId" }
                    }, onError = {
                        Timber.e(it) { "Offline cache sync error for documentId: $documentId (rescheduling)" }
                    })
        }

        Timber.d { "Offline cache sync job complete." }

        return Result.success()
    }

    companion object {
        const val DOCUMENT_IDS_KEY = "DOCUMENT_IDS_KEY"
    }
}