package de.markusressel.mkdocseditor.feature.editor.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Saved state is a Bundle, so this needs Robolectric */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class ChangeJumpArgumentsTest {

    private val headings = listOf("Fruit", "Drinks")

    @Test
    fun `a page opened at a change goes there once`() {
        val handle = SavedStateHandle()
        assertEquals(headings, ChangeJumpArguments(handle).take(headings))
        // a rotation keeps the view model, and the screen hands its arguments again
        assertNull(ChangeJumpArguments(handle).take(headings))
    }

    @Test
    fun `the arguments that Android restores after process death do not move the page again`() {
        val handle = SavedStateHandle()
        ChangeJumpArguments(handle).take(headings)
        val restored = SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)
        assertNull(ChangeJumpArguments(restored).take(headings))
    }

    @Test
    fun `a page opened without headings has nowhere to go`() {
        assertNull(ChangeJumpArguments(SavedStateHandle()).take(emptyList()))
    }
}
