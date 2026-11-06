package de.markusressel.mkdocseditor.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class WikiChangesWorkerTest {

    private lateinit var context: Application
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
    }

    private fun work() = workManager.getWorkInfosForUniqueWork(WikiChangesWorker.WORK_NAME).get()

    @Test
    fun `the check runs about once an hour, with a network and enough battery`() {
        WikiChangesWorker.schedule(context)

        val work = work().single()
        assertEquals(TimeUnit.HOURS.toMillis(1), work.periodicityInfo?.repeatIntervalMillis)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertTrue(work.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `scheduling again keeps the one check`() {
        WikiChangesWorker.schedule(context)
        WikiChangesWorker.schedule(context)

        assertEquals(1, work().size)
    }

    @Test
    fun `turning notifications off stops the check`() {
        WikiChangesWorker.schedule(context)

        WikiChangesWorker.cancel(context)

        assertEquals(WorkInfo.State.CANCELLED, work().single().state)
    }
}
