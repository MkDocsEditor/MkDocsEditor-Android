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
import de.markusressel.mkdocseditor.feature.filebrowser.ui.ApplyCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.ErrorResult
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.deserializer
import kotlinx.coroutines.coroutineScope


@HiltWorker
internal class OfflineSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val dataRepository: DataRepository,
    private val restClient: IMkDocsRestClient,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager,
    private val applyCurrentBackendConfigUseCase: ApplyCurrentBackendConfigUseCase,
    private val preferencesHolder: KutePreferencesHolder,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        val documentIds = inputData.getStringArray(DOCUMENT_IDS_KEY)
        if (documentIds == null) {
            Timber.wtf { "Missing documentIds parameter!" }
            return@coroutineScope Result.failure()
        }

        try {
            applyCurrentBackendConfigUseCase()
        } catch (ex: Exception) {
            Timber.e(ex)
            return@coroutineScope Result.failure()
        }

        Timber.d { "Scheduling background thread for updating offline cache of ${documentIds.size} documents..." }
        // use random order to evenly distribute probability of caching a document
        documentIds.toSet().shuffled().forEach { documentId ->
            restClient.getDocumentContent(documentId).fold(success = { text ->
                try {
                    documentContentPersistenceManager.insertOrUpdate(
                        documentId = documentId,
                        text = text
                    )
                    Timber.d { "Offline cache sync finished successfully for documentId: $documentId" }
                } catch (e: Exception) {
                    Timber.e(e) { "Offline cache sync error for documentId: $documentId (rescheduling)" }
                }
            }, failure = {
                try {
                    val errorResult = deserializer<ErrorResult>().deserialize(it.response)
                    Timber.e { "Offline cache sync error for documentId: $documentId: ${errorResult.message} (rescheduling)" }
                } catch (e: Exception) {
                    Timber.e(e)
                    Timber.e(it) { "Offline cache sync error for documentId: $documentId (rescheduling)" }
                }
            })
        }

        Timber.d { "Offline cache sync job complete." }
        preferencesHolder.lastOfflineCacheUpdate.updateToNow()
        Result.success()
    }

    companion object {
        const val DOCUMENT_IDS_KEY = "DOCUMENT_IDS_KEY"
    }
}