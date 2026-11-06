package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class SyncStatusStoreTest {

    private val store = SyncStatusStore(ApplicationProvider.getApplicationContext())

    @Test
    fun `a screen that watches the last sync sees one made elsewhere`() = runTest(UnconfinedTestDispatcher()) {
        store.setLastSync(1, Date(1_000))
        val seen = mutableListOf<Date?>()
        backgroundScope.launch { store.lastSyncFlow(1).toList(seen) }

        // e.g. the editor got an answer, and another backend synced too
        store.setLastSync(1, Date(2_000))
        store.setLastSync(2, Date(3_000))

        assertEquals(listOf(Date(1_000), Date(2_000)), seen)
    }
}
