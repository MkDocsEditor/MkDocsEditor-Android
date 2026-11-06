package de.markusressel.mkdocseditor.feature.editor.ui.compose

import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EditorTopBarActionsTest {

    private val editing = CodeEditorViewModel.UiState(editModeActive = true)

    private fun menuOf(actions: List<TopAppBarAction.CodeEditor>) =
        assertIs<TopAppBarAction.CodeEditor.MoreAction>(actions.last()).entries

    @Test
    fun `on a phone the author colors and the references go into the menu`() {
        val actions = editorTopBarActions(editing, SplitMode.Off, wide = false)

        assertEquals(
            listOf(TopAppBarAction.CodeEditor.EditorModeAction::class, TopAppBarAction.CodeEditor.MoreAction::class),
            actions.map { it::class },
        )
        val menu = menuOf(actions)
        assertTrue(menu.any { it is TopAppBarAction.CodeEditor.AuthorHighlightAction })
        assertTrue(TopAppBarAction.CodeEditor.PageReferencesAction in menu)
    }

    @Test
    fun `a wide window keeps them in the bar`() {
        val actions = editorTopBarActions(editing, SplitMode.Off, wide = true)

        assertTrue(actions.any { it is TopAppBarAction.CodeEditor.AuthorHighlightAction })
        assertTrue(TopAppBarAction.CodeEditor.PageReferencesAction in actions)
        assertTrue(menuOf(actions).none { it is TopAppBarAction.CodeEditor.AuthorHighlightAction || it == TopAppBarAction.CodeEditor.PageReferencesAction })
    }

    @Test
    fun `the page can be renamed, moved and deleted from the menu, last`() {
        val menu = menuOf(editorTopBarActions(CodeEditorViewModel.UiState(), SplitMode.Off, wide = false))

        assertEquals(
            listOf(
                TopAppBarAction.CodeEditor.RenameAction,
                TopAppBarAction.CodeEditor.MoveAction,
                TopAppBarAction.CodeEditor.DeleteAction,
            ),
            menu.takeLast(3),
        )
    }
}
