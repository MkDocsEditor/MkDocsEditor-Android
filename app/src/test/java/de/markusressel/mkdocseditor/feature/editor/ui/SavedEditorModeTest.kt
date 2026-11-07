package de.markusressel.mkdocseditor.feature.editor.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.editing
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.initialUiState
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Saved state is a Bundle, so this needs Robolectric */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class SavedEditorModeTest {

    /** what Android hands to the screen after it killed the app */
    private fun afterProcessDeath(handle: SavedStateHandle) =
        SavedEditorMode(SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null))

    @Test
    fun `the edit mode and the split come back after process death`() {
        val handle = SavedStateHandle()
        SavedEditorMode(handle).save(initialUiState(editModeOpening = false).editing().copy(splitMode = SplitMode.Vertical))

        val restored = afterProcessDeath(handle)

        assertTrue(restored.editing)
        assertEquals(SplitMode.Vertical, restored.splitMode)
    }

    @Test
    fun `an edit mode that is still opening counts as editing`() {
        val handle = SavedStateHandle()
        SavedEditorMode(handle).save(initialUiState(editModeOpening = true))

        assertTrue(afterProcessDeath(handle).editing)
    }

    @Test
    fun `reading comes back as reading, with the split that fits the window`() {
        val handle = SavedStateHandle()
        val mode = SavedEditorMode(handle)
        mode.save(initialUiState(editModeOpening = false).editing().copy(splitMode = SplitMode.Horizontal))
        mode.save(initialUiState(editModeOpening = false))

        val restored = afterProcessDeath(handle)

        assertFalse(restored.editing)
        assertNull(restored.splitMode)
    }

    @Test
    fun `a new screen starts reading`() {
        val mode = SavedEditorMode(SavedStateHandle())

        assertFalse(mode.editing)
        assertNull(mode.splitMode)
    }
}
