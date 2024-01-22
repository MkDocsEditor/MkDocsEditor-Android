package de.markusressel.mkdocseditor.service

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Handler
import android.os.Looper
import com.github.ajalt.timberkt.Timber
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.mkdocseditor.data.persistence.DocumentContentPersistenceManager
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Service to handle callbacks from the JobScheduler. Requests scheduled with the JobScheduler
 * ultimately land on this service's "onStartJob" method. It runs jobs for a specific amount of time
 * and finishes them. It keeps the activity updated with changes via a Messenger.
 */
@AndroidEntryPoint
class OfflineCacheSyncService : JobService() {

    @Inject
    internal lateinit var restClient: IMkDocsRestClient

    @Inject
    internal lateinit var preferencesHolder: KutePreferencesHolder

    @Inject
    internal lateinit var documentContentPersistenceManager: DocumentContentPersistenceManager

    private val handler = Handler(Looper.getMainLooper())

    override fun onStartJob(params: JobParameters): Boolean {
        val documentIds = params.extras.getStringArray(DOCUMENT_IDS_KEY)
        if (documentIds == null) {
            Timber.wtf { "Missing documentIds parameter!" }
            return false
        }

        Timber.d { "Scheduling background thread for updating offline cache of ${documentIds.size} documents..." }
        handler.postDelayed({
            // use random order to evenly distribute probability of caching a document
            documentIds.toSet().shuffled().forEach { documentId ->
                CoroutineScope(Dispatchers.IO).launch {
                    restClient.getDocumentContent(documentId).fold(success = { text ->
                        documentContentPersistenceManager.insertOrUpdate(
                            documentId = documentId,
                            text = text
                        )

                        Timber.d { "Offline cache sync finished successfully for documentId: $documentId" }
                        jobFinished(params, false)
                    }, failure = {
                        Timber.e(it) { "Offline cache sync error for documentId: $documentId (rescheduling)" }
                        jobFinished(params, true)
                    })
                }
            }

            Timber.d { "Offline cache sync job complete." }
            preferencesHolder.lastOfflineCacheUpdate.updateToNow()
        }, 1000)
        Timber.d { "on start job: ${params.jobId}" }

        // Return true as there's more work to be done with this job.
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        Timber.d { "Offline cache sync job cancelled." }
        handler.removeCallbacksAndMessages(null)
        return false // don't reschedule, we will try again on next app opening
    }

    companion object {
        const val DOCUMENT_IDS_KEY = "DOCUMENT_IDS_KEY"
    }
}