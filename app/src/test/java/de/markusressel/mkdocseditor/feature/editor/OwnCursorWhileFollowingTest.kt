package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.ownCursorPlaced
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Following
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.UiState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OwnCursorWhileFollowingTest {

    private val following = Following("instance", "anna · Pixel")

    /** what following opens for someone who was reading the rendered page */
    private val openedByFollowing = UiState(following = following, readOnlyEditorShown = true, canEdit = true)

    @Test
    fun `a cursor in an editor that following opened ends following and can be typed at`() {
        val state = openedByFollowing.ownCursorPlaced(offline = false)

        assertNull(state.following)
        assertTrue(state.editModeActive)
        assertTrue(state.isBottomAppBarVisible)
    }

    @Test
    fun `a reader gets the cursor and the end of following, but no edit mode`() {
        val state = openedByFollowing.copy(canEdit = false).ownCursorPlaced(offline = false)

        assertNull(state.following)
        assertFalse(state.editModeActive)
    }

    @Test
    fun `offline the edit mode stays off, as it does for a join link`() {
        assertFalse(openedByFollowing.ownCursorPlaced(offline = true).editModeActive)
    }

    @Test
    fun `a cursor while editing only ends following`() {
        val editing = UiState(following = following, editModeActive = true, canEdit = true)

        assertEquals(editing.copy(following = null), editing.ownCursorPlaced(offline = false))
    }

    @Test
    fun `without following a cursor changes nothing`() {
        val reading = UiState(readOnlyEditorShown = true, canEdit = true)

        assertEquals(reading, reading.ownCursorPlaced(offline = false))
    }
}
