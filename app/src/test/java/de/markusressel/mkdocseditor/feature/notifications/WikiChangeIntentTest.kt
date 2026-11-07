package de.markusressel.mkdocseditor.feature.notifications

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import de.markusressel.mkdocseditor.feature.editor.domain.ChangedLines
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
    fun `tapping a notification opens the page at the change it was about`() {
        val location = ChangeLocation(listOf("Fruit", "Drinks"), "9f2c1a0b7d3e4f55", listOf(ChangedLines(12, 3, "Fruit", 1, 4)))
        val change = PageChange("doc1", "Shopping", listOf("Anna"), listOf("Fruit", "Drinks"), Date(0), location)
        val intent = openIntent(ApplicationProvider.getApplicationContext(), 7L, change)

        assertEquals("doc1", intent.getStringExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT))
        assertEquals(7L, intent.getLongExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT_BACKEND, -1L))
        assertEquals(location, intent.getSerializableExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT_CHANGE, ChangeLocation::class.java))
    }
}
