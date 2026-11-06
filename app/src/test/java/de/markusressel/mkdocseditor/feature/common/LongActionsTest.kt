package de.markusressel.mkdocseditor.feature.common

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import de.markusressel.mkdocseditor.feature.common.data.percentSteps
import de.markusressel.mkdocseditor.feature.common.ui.compose.TransferProgressBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class LongActionsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `an upload shows what it has sent of the whole file, and can be cancelled`() {
        var cancelled = 0
        composeRule.setContent {
            MkDocsEditorTheme {
                TransferProgressBar(
                    progress = TransferProgress(1, "holiday.mp4", upload = true, doneBytes = 12_000_000, totalBytes = 50_000_000),
                    onCancel = { cancelled++ },
                )
            }
        }

        composeRule.onNodeWithText("Uploading holiday.mp4").assertExists()
        composeRule.onNodeWithText("12 MB of 50 MB").assertExists()
        composeRule.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelled)
    }

    @Test
    fun `progress passes on a step of a percent, not every few kilobytes`() {
        val steps = mutableListOf<Long>()
        val onProgress = percentSteps { done, _ -> steps += done }

        (0L..1_000L).forEach { onProgress(it, 1_000L) }

        assertEquals(101, steps.size)
        assertEquals(1_000L, steps.last())
    }

    @Test
    fun `a size that is not known yet has no fraction`() {
        assertNull(TransferProgress(1, "a", upload = false, doneBytes = 10).fraction)
        assertEquals(0.5f, TransferProgress(1, "a", upload = false, doneBytes = 5, totalBytes = 10).fraction)
    }
}
