package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.ui.compose.EditorChromeState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorChromeStateTest {

    private val state = EditorChromeState()

    @Test
    fun `opening the keyboard alone keeps the top bar`() {
        state.keyboardVisible = true
        assertTrue(state.topBarVisible)
    }

    @Test
    fun `typing with the keyboard hides the top bar`() {
        state.keyboardVisible = true
        state.onTextEdited()
        assertFalse(state.topBarVisible)
    }

    @Test
    fun `edits without the keyboard keep the top bar`() {
        state.onTextEdited()
        assertTrue(state.topBarVisible)
    }

    @Test
    fun `scrolling up brings the top bar back until the next edit`() {
        state.keyboardVisible = true
        state.onTextEdited()
        state.onDraggedDown()
        assertTrue(state.topBarVisible)
        state.onTextEdited()
        assertFalse(state.topBarVisible)
    }

    @Test
    fun `on a short screen the keyboard alone hides the top bar`() {
        val short = EditorChromeState(shortScreen = true)
        short.keyboardVisible = true
        assertFalse(short.topBarVisible)
        short.onDraggedDown()
        assertTrue(short.topBarVisible)
        short.keyboardVisible = false
        assertTrue(short.topBarVisible)
    }

    @Test
    fun `closing the keyboard brings the top bar back`() {
        state.keyboardVisible = true
        state.onTextEdited()
        state.keyboardVisible = false
        assertTrue(state.topBarVisible)
    }
}
