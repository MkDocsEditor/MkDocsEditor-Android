package de.markusressel.mkdocseditor.feature.editor.ui.compose

import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionIndicator
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EditorTopBarActionsTest {

    private val editing = CodeEditorViewModel.UiState(editModeActive = true, historyAvailable = true)

    /** a phone in portrait */
    private val phone = 411

    /** so narrow that the title needs the room of the view settings */
    private val narrow = 320

    private val wide = WIDE_TOP_BAR_DP

    private fun menuOf(actions: List<TopAppBarAction.CodeEditor>) =
        assertIs<TopAppBarAction.CodeEditor.MoreAction>(actions.last()).entries

    @Test
    fun `on a phone the references go into the menu, the view settings stay in the bar while the title fits`() {
        val actions = editorTopBarActions(editing, SplitMode.Off, widthDp = phone)

        assertEquals(
            listOf(
                TopAppBarAction.CodeEditor.ViewSettingsAction::class,
                TopAppBarAction.CodeEditor.EditorModeAction::class,
                TopAppBarAction.CodeEditor.MoreAction::class,
            ),
            actions.map { it::class },
        )
        assertEquals(TopAppBarAction.CodeEditor.PageReferencesAction, menuOf(actions).first())
    }

    @Test
    fun `without room for the title the view settings are the first entry of the menu`() {
        val crowded = editing.copy(
            connectionIndicator = ConnectionIndicator.Connected,
            remotePeers = listOf(RemotePeer(clientId = "1", username = "alice", displayName = "Alice", color = null, selections = emptyList())),
        )

        for ((uiState, width) in listOf(editing to narrow, crowded to phone)) {
            val actions = editorTopBarActions(uiState, SplitMode.Off, widthDp = width)

            assertFalse(TopAppBarAction.CodeEditor.ViewSettingsAction in actions)
            assertEquals(TopAppBarAction.CodeEditor.ViewSettingsAction, menuOf(actions).first())
        }
    }

    @Test
    fun `a wide window keeps the view settings and the references in the bar`() {
        val actions = editorTopBarActions(editing, SplitMode.Off, widthDp = wide)

        assertTrue(TopAppBarAction.CodeEditor.ViewSettingsAction in actions)
        assertTrue(TopAppBarAction.CodeEditor.PageReferencesAction in actions)
        val menu = menuOf(actions)
        assertTrue(TopAppBarAction.CodeEditor.ViewSettingsAction !in menu && TopAppBarAction.CodeEditor.PageReferencesAction !in menu)
        // the history stays in the menu, it opens a screen of its own
        assertEquals(TopAppBarAction.CodeEditor.HistoryAction, menu.first())
    }

    @Test
    fun `while reading there are no view settings, the page shows no editor`() {
        for (width in listOf(narrow, phone, wide)) {
            val actions = editorTopBarActions(CodeEditorViewModel.UiState(), SplitMode.Vertical, widthDp = width)

            assertTrue(TopAppBarAction.CodeEditor.ViewSettingsAction !in actions)
            assertTrue(TopAppBarAction.CodeEditor.ViewSettingsAction !in menuOf(actions))
        }
    }

    @Test
    fun `the menu holds only actions, no view settings`() {
        val actionsOnly = setOf(
            TopAppBarAction.CodeEditor.ViewSettingsAction,
            TopAppBarAction.CodeEditor.PageReferencesAction,
            TopAppBarAction.CodeEditor.HistoryAction,
            TopAppBarAction.CodeEditor.MenuDivider,
            TopAppBarAction.CodeEditor.ShareLinkAction,
            TopAppBarAction.CodeEditor.ShowInBrowserAction,
            TopAppBarAction.CodeEditor.RenameAction,
            TopAppBarAction.CodeEditor.MoveAction,
            TopAppBarAction.CodeEditor.PinAction,
            TopAppBarAction.CodeEditor.DeleteAction,
        )
        for (split in SplitMode.entries) for (width in listOf(narrow, phone, wide)) {
            val menu = menuOf(editorTopBarActions(editing, split, widthDp = width))

            // the view settings may open the sheet from there, but no setting is changed in the menu itself
            assertTrue(menu.all { it in actionsOnly }, "$split at $width dp: $menu")
        }
    }

    @Test
    fun `the history is in the menu only when the server has it`() {
        val without = menuOf(editorTopBarActions(editing.copy(historyAvailable = false), SplitMode.Off, widthDp = phone))
        val with = menuOf(editorTopBarActions(editing, SplitMode.Off, widthDp = phone))

        assertTrue(TopAppBarAction.CodeEditor.HistoryAction !in without)
        assertTrue(TopAppBarAction.CodeEditor.HistoryAction in with)
    }

    @Test
    fun `the page can be renamed, moved, pinned and deleted from the menu, last`() {
        val menu = menuOf(editorTopBarActions(CodeEditorViewModel.UiState(), SplitMode.Off, widthDp = phone))

        assertEquals(
            listOf(
                TopAppBarAction.CodeEditor.RenameAction,
                TopAppBarAction.CodeEditor.MoveAction,
                TopAppBarAction.CodeEditor.PinAction,
                TopAppBarAction.CodeEditor.DeleteAction,
            ),
            menu.takeLast(4),
        )
    }

    @Test
    fun `the text size slider stops at each whole size of the setting`() {
        // 10, 11, ..., 24: the ends and 13 stops between them
        assertEquals(13, textSizeSliderSteps(10..24))
        assertEquals(0, textSizeSliderSteps(10..10))
    }
}
