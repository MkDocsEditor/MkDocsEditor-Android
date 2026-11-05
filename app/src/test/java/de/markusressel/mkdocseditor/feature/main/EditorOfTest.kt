package de.markusressel.mkdocseditor.feature.main

import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreen
import de.markusressel.mkdocseditor.feature.main.data.editorOf
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class EditorOfTest {

    @Test
    fun `a join link goes to the editor of its document nearest to the top`() {
        val lower = CodeEditorScreen("doc")
        val other = CodeEditorScreen("other")
        val upper = CodeEditorScreen("doc")

        assertSame(upper, listOf(lower, other, upper).editorOf("doc"))
        assertSame(lower, listOf(lower, other).editorOf("doc"))
    }

    @Test
    fun `without an editor of the document the link opens a new one`() {
        assertNull(listOf(CodeEditorScreen("other")).editorOf("doc"))
    }
}
