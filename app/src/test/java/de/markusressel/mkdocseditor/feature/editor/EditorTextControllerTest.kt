package de.markusressel.mkdocseditor.feature.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
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

        override fun splicesSince(version: DocumentVersion, text: String) = automerge.splicesSince(version, text)

        /** what a sync message from another user does */
        fun remoteEdit(newText: String): List<TextSplice> {
            val other = AutomergeTextDocument.fromDocument(automerge.document.fork())
            other.applyLocalText(newText)
            return automerge.merge(other)
        }
    }

    private var editMode = true
    private var edits = 0

    private fun TestScope.controller() = EditorTextController(
        scope = backgroundScope,
        now = { currentTime },
        editModeActive = { editMode },
        onEdited = { edits++ },
    )

    @Test
    fun `a bound document shows up in the field`() = runTest {
        val controller = controller()
        controller.bind(TestDocument("hello"))
        assertEquals("hello", controller.field.value.value.text)
        assertEquals("hello", controller.text)
    }

    @Test
    fun `typing goes into the document, but not outside of the edit mode`() = runTest {
        val document = TestDocument("hello")
        val controller = controller()
        controller.bind(document)

        controller.onFieldChanged(TextFieldValue("hello!", TextRange(6)))
        assertEquals("hello!", document.text)

        editMode = false
        controller.onFieldChanged(TextFieldValue("hello!?", TextRange(7)))
        assertEquals("hello!", document.text)
    }

    @Test
    fun `formatting wraps the selection, and counts as an edit`() = runTest {
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
    fun `a selection outside of the text stays inside of it`() = runTest {
        val controller = controller()
        controller.bind(TestDocument("abc"))
        controller.select(-5, 99)
        assertEquals(TextRange(0, 3), controller.selection)
    }

    @Test
    fun `markdown replaces the selection, or goes after it`() = runTest {
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
    fun `a replaced range keeps the rest of the text`() = runTest {
        val document = TestDocument("[a](old.md) b")
        val controller = controller()
        controller.bind(document)
        controller.replace(4, 10, "new.md")
        assertEquals("[a](new.md) b", document.text)
    }

    @Test
    fun `a remote change waits while the user types, then shows up`() = runTest {
        val document = TestDocument("hello")
        val controller = controller()
        controller.bind(document)
        controller.onValueShown(controller.field.value.id)
        controller.onFocusChanged(true)
        controller.onFieldChanged(TextFieldValue("hello!", TextRange(6)))
        controller.onValueShown(controller.field.value.id)

        val splices = document.remoteEdit("remote hello!")
        controller.onDocumentChanged(splices)
        assertEquals("remote hello!", controller.text)
        assertFalse(controller.field.value.value.text.startsWith("remote"))

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("remote hello!", controller.field.value.value.text)
    }

    @Test
    fun `other users are resolved against the field`() = runTest {
        val controller = controller()
        controller.bind(TestDocument("hello"))
        val peer = RemotePeer("client", "other", null, null, listOf(RemoteSelection(2, 3)))

        controller.onPeersChanged(listOf(peer))
        assertEquals(listOf(peer), controller.field.value.remotePeers)

        controller.bind(TestDocument("other"))
        assertTrue(controller.field.value.remotePeers.isEmpty())
    }

    @Test
    fun `a detached text is shown until the document has a text`() = runTest {
        val controller = controller()
        controller.showDetachedText("cached")
        assertEquals("cached", controller.field.value.value.text)

        controller.bind(TestDocument("server"))
        controller.showDetachedText("cached")
        assertEquals("server", controller.field.value.value.text)
    }
}
