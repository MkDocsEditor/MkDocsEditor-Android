package de.markusressel.mkdocseditor.feature.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.notifications.domain.PageChange
import de.markusressel.mkdocseditor.feature.notifications.ui.openIntent
import de.markusressel.mkdocseditor.ui.activity.MainActivityCompose
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class WikiChangeIntentTest {

    @Test
    fun `tapping a notification opens the page at the headings it was about`() {
        val change = PageChange("doc1", "Shopping", listOf("Anna"), listOf("Fruit", "Drinks"), Date(0))
        val intent = openIntent(ApplicationProvider.getApplicationContext(), 7L, change)

        assertEquals("doc1", intent.getStringExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT))
        assertEquals(7L, intent.getLongExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT_BACKEND, -1L))
        assertEquals(listOf("Fruit", "Drinks"), intent.getStringArrayListExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT_HEADINGS))
    }
}
