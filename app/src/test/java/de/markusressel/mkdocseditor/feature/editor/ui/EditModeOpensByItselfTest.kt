package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.Companion.editModeOpensByItself
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditModeOpensByItselfTest {

    /** Before, it waited for the connection, and a server that did not answer turned it into the page after 15 s */
    @Test
    fun `a local copy opens the edit mode without waiting for the server`() {
        assertTrue(editModeOpensByItself(editable = true, offlineMode = false, wanted = true))
    }

    @Test
    fun `it stays closed when nothing can be edited, in the offline mode, or when the user does not want it`() {
        assertFalse(editModeOpensByItself(editable = false, offlineMode = false, wanted = true))
        assertFalse(editModeOpensByItself(editable = true, offlineMode = true, wanted = true))
        assertFalse(editModeOpensByItself(editable = true, offlineMode = false, wanted = false))
    }
}
