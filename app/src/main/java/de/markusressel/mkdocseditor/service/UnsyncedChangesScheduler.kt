package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.newValues
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the [UnsyncedChangesWorker] whenever there is something to upload. WorkManager runs it
 * once there is a network, and keeps it across restarts of the app.
 */
@Singleton
internal class UnsyncedChangesScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: LocalDocumentStore,
    private val pendingUploads: PendingUploads,
    private val networkManager: NetworkManager,
    private val authRepository: AuthRepository,
) {
    fun start(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) { store.scanUnsynced() }

        scope.launch {
            pendingUploads.flow.collect { if (it.isNotEmpty()) enqueue(ExistingWorkPolicy.KEEP) }
        }

        // the server may be reachable now (a new network, a VPN), or may accept us again:
        // do not sit out the waiting time of the last failed try
        scope.launch {
            merge(
                networkManager.defaultNetwork.newValues().map { },
                authRepository.sessionChanges.map { },
            ).collect {
                if (pendingUploads.current().isNotEmpty()) enqueue(ExistingWorkPolicy.REPLACE)
            }
        }
    }

    private fun enqueue(policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<UnsyncedChangesWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, RETRY_DELAY_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
    }

    private companion object {
        const val WORK_NAME = "upload-unsynced-changes"
        const val RETRY_DELAY_MINUTES = 1L
    }
}
