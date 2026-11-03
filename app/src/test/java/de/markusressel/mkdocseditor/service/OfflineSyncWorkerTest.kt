package de.markusressel.mkdocseditor.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker.Result
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class OfflineSyncWorkerTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `scheduling twice gives one sync`() {
        workManager.enqueueOfflineSync()
        workManager.enqueueOfflineSync()

        assertEquals(1, workManager.getWorkInfosForUniqueWork(OFFLINE_SYNC_WORK_NAME).get().size)
    }

    @Test
    fun `a sync waits for a network`() {
        workManager.enqueueOfflineSync()

        val work = workManager.getWorkInfosForUniqueWork(OFFLINE_SYNC_WORK_NAME).get().single()
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `a complete run and a missing session are not tried again`() {
        assertEquals(Result.success(), CacheOutcome.Complete.toWorkResult(attempt = 0))
        assertEquals(Result.success(), CacheOutcome.NotSignedIn.toWorkResult(attempt = 0))
    }

    @Test
    fun `failures are tried again a limited number of times`() {
        assertEquals(Result.retry(), CacheOutcome.SomeFailed.toWorkResult(attempt = 0))
        assertEquals(Result.retry(), CacheOutcome.SomeFailed.toWorkResult(attempt = MAX_OFFLINE_SYNC_ATTEMPTS - 2))
        assertEquals(Result.success(), CacheOutcome.SomeFailed.toWorkResult(attempt = MAX_OFFLINE_SYNC_ATTEMPTS - 1))
    }
}
