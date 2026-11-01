package de.markusressel.mkdocseditor.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * See [UploadUnsyncedChanges]. Also runs after the app is gone, so changes made offline do not
 * wait for the page to be opened again.
 */
@HiltWorker
internal class UnsyncedChangesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val uploadUnsyncedChanges: UploadUnsyncedChanges,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = when (uploadUnsyncedChanges()) {
        UploadUnsyncedChanges.Result.Finished -> Result.success()
        UploadUnsyncedChanges.Result.TryAgainLater -> Result.retry()
    }
}
