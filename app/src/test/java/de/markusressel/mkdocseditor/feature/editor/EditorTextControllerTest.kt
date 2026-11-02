package de.markusressel.mkdocseditor.feature.editor

import androidx.compose.ui.text.TextRange
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorTextControllerTest {

    private class TestDocument(text: String) : EditableTextDocument {
        val automerge: AutomergeTextDocument = run {
            val document = Document()
            document.startTransaction().use { tx ->
                val content = tx.set(ObjectId.ROOT, AutomergeTextDocument.CONTENT_KEY, ObjectType.TEXT)
                tx.spliceText(content, 0, 0, text)
                tx.commit()
            }
            AutomergeTextDocument.fromDocument(document)
        }
        override val text: String get() = automerge.text
        override val version: DocumentVersion get() = automerge.version
        override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) =
            automerge.applySplicesAt(base, baseText, splices)


        /** what a sync message from another user does */
        fun remoteEdit(newText: String): List<TextSplice> {
            val other = AutomergeTextDocument.fromDocument(automerge.document.fork())
            other.applyLocalText(newText)
            return automerge.merge(other)
        }
    }

    private var editMode = true
    private var edits = 0
    private val selections = mutableListOf<TextRange>()

    private fun controller() = EditorTextController(
        editModeActive = { editMode },
        onSelectionChanged = { selections += it },
        onEdited = { edits++ },
    )

    @Test
    fun `a bound document shows up in the editor`() {
        val controller = controller()
        controller.bind(TestDocument("hello"))
        assertEquals("hello", controller.editorState.text)
        assertEquals("hello", controller.text)
    }

    @Test
    fun `typing goes into the document, but not outside of the edit mode`() {
        val document = TestDocument("hello")
        val controller = controller()
        controller.bind(document)
        controller.select(5, 5)

        controller.editorState.replaceSelection("!")
        assertEquals("hello!", document.text)
        assertEquals(0, edits)

        editMode = false
        controller.editorState.replaceSelection("?")
        assertEquals("hello!", document.text)
        assertEquals("hello!", controller.text)
    }

    @Test
    fun `formatting wraps the selection, and counts as an edit`() {
        val document = TestDocument("one two")
        val controller = controller()
        controller.bind(document)
        controller.select(4, 7)
        assertEquals("two", controller.selectedText)

        controller.applyFormat(FormatAction.Bold)

        assertEquals("one **two**", document.text)
        assertEquals(1, edits)
    }

    @Test
    fun `a selection outside of the text stays inside of it`() {
        val controller = controller()
        controller.bind(TestDocument("abc"))
        controller.select(-5, 99)
        assertEquals(TextRange(0, 3), controller.selection)
    }

    @Test
    fun `markdown replaces the selection, or goes after it`() {
        val document = TestDocument("abc")
        val controller = controller()
        controller.bind(document)

        controller.select(1, 2)
        controller.insertAtSelection("X")
        assertEquals("aXc", document.text)

        controller.select(0, 1)
        controller.insertAfterSelection("Y")
        assertEquals("aYXc", document.text)
        assertEquals(2, edits)
    }

    @Test
    fun `a replaced range keeps the rest of the text`() {
        val document = TestDocument("[a](old.md) b")
        val controller = controller()
        controller.bind(document)
        controller.replace(4, 10, "new.md")
        assertEquals("[a](new.md) b", document.text)
    }

    @Test
    fun `a remote change shows up at once, also while the user types`() {
        val document = TestDocument("hello")
        val controller = controller()
        controller.bind(document)
        controller.select(5, 5)
        controller.editorState.replaceSelection("!")

        val splices = document.remoteEdit("remote hello!")
        controller.onRemoteChange(document.text, splices)

        assertEquals("remote hello!", controller.text)
        assertEquals(TextRange(13), controller.selection)
        controller.editorState.replaceSelection("?")
        assertEquals("remote hello!?", document.text)
    }

    @Test
    fun `an editor that differs from the document after a remote change takes its text`() {
        val document = TestDocument("hello")
        val controller = controller()
        controller.bind(document)

        val splices = document.remoteEdit("hello world")
        controller.onRemoteChange(document.text, splices.drop(1) + TextSplice(0, 0, "wrong"))

        assertEquals("hello world", controller.text)
    }

    @Test
    fun `only the user moves the reported selection`() {
        val document = TestDocument("hello")
        val controller = controller()
        controller.bind(document)
        assertTrue(selections.isEmpty())

        controller.select(5, 5)
        assertEquals(listOf(TextRange(5)), selections)

        val splices = document.remoteEdit(">> hello")
        controller.onRemoteChange(document.text, splices)
        assertEquals(TextRange(8), controller.selection)
        assertEquals(listOf(TextRange(5)), selections)

        controller.select(0, 0)
        assertEquals(listOf(TextRange(5), TextRange(0)), selections)
    }

    @Test
    fun `other users are kept until another document comes`() {
        val controller = controller()
        controller.bind(TestDocument("hello"))
        val peer = RemotePeer("client", "other", null, null, listOf(RemoteSelection(2, 3)))

        controller.onPeersChanged(listOf(peer))
        assertEquals(listOf(peer), controller.peers.value)

        controller.bind(TestDocument("other"))
        assertTrue(controller.peers.value.isEmpty())
    }

    @Test
    fun `a detached text is shown until the document has a text`() {
        val controller = controller()
        controller.showDetachedText("cached")
        assertEquals("cached", controller.text)

        controller.bind(TestDocument("server"))
        controller.showDetachedText("cached")
        assertEquals("server", controller.text)
    }
}
