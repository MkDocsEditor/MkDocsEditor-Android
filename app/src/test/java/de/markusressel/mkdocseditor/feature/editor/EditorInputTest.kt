package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorLayout
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The real editor with its real [InputConnection], the way an input method talks
 * to it, connected to a real Automerge document with [EditorTextController].
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorInputTest {

    @get:Rule
    val composeRule = createComposeRule()

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
        fun remoteEdit(newText: String, controller: EditorTextController) {
            val other = automerge.document.fork()
            val otherDocument = AutomergeTextDocument.fromDocument(other)
            otherDocument.applyLocalText(newText)
            val splices = automerge.merge(otherDocument)
            controller.onRemoteChange(text, splices)
        }
    }

    private lateinit var document: TestDocument
    private lateinit var controller: EditorTextController
    private lateinit var view: View

    private val state get() = controller.editorState

    private var editMode by mutableStateOf(true)

    /** starts the editor with the caret at [caret] and returns its input connection */
    private fun startEditor(text: String, caret: Int): InputConnection {
        document = TestDocument(text)
        controller = EditorTextController(editModeActive = { editMode })
        controller.bind(document)
        controller.select(caret, caret)
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme {
                // two calls, like in the screen: each mode gets its own editor
                if (editMode) {
                    CodeEditorLayout(modifier = Modifier.size(400.dp, 400.dp), state = state, readOnly = false)
                } else {
                    CodeEditorLayout(modifier = Modifier.size(400.dp, 400.dp), state = state, readOnly = true)
                }
            }
        }
        composeRule.waitForIdle()
        return focusEditor()
    }

    private fun focusEditor(): InputConnection {
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        return connection()
    }

    private fun connection(): InputConnection = assertNotNull(view.onCreateInputConnection(EditorInfo()))

    private fun remote(newText: String) = onUi { document.remoteEdit(newText, controller) }

    private fun onUi(block: () -> Unit) = composeRule.runOnUiThread(block)

    @Test
    fun `a key press in the same frame as a remote change keeps both`() {
        val input = startEditor("Hello", 5)
        composeRule.mainClock.autoAdvance = false

        // no frame between the remote change and the key press
        onUi {
            document.remoteEdit("HelloX", controller)
            input.commitText("Y", 1)
        }
        composeRule.mainClock.advanceTimeByFrame()

        assertTrue(document.text.contains("X"), document.text)
        assertTrue(document.text.contains("Y"), document.text)
        assertEquals(document.text, state.text)
    }

    @Test
    fun `key presses in one frame all arrive`() {
        val input = startEditor("", 0)
        composeRule.mainClock.autoAdvance = false
        onUi {
            input.commitText("a", 1)
            input.commitText("b", 1)
            input.commitText("c", 1)
        }
        composeRule.mainClock.advanceTimeByFrame()
        onUi { input.commitText("d", 1) }
        composeRule.mainClock.advanceTimeByFrame()
        assertEquals("abcd", document.text)
        assertEquals("abcd", state.text)
        assertEquals(TextRange(4), state.selection)
    }

    @Test
    fun `a composition survives a remote change and every state reaches the document`() {
        val input = startEditor("Text ", 5)

        onUi { input.setComposingText("M", 1) }
        composeRule.waitForIdle()
        assertEquals(TextRange(5, 6), state.composition)

        // the same input connection stays in use: nothing restarted the input method
        remote("REMOTE Text M")
        composeRule.waitForIdle()
        assertEquals("REMOTE Text M", state.text)
        assertEquals(TextRange(12, 13), state.composition)

        onUi { input.setComposingText("Mü", 1) }
        assertEquals("REMOTE Text Mü", document.text)
        assertEquals(TextRange(12, 14), state.composition)

        onUi { input.setComposingText("Müller", 1) }
        assertEquals(TextRange(12, 18), state.composition)

        onUi { input.finishComposingText() }
        composeRule.waitForIdle()
        assertEquals("REMOTE Text Müller", document.text)
        assertEquals("REMOTE Text Müller", state.text)
        assertEquals(TextRange("REMOTE Text Müller".length), state.selection)
        assertNull(state.composition)
    }

    @Test
    fun `typing without any remote change keeps the composition`() {
        val input = startEditor("", 0)
        onUi { input.setComposingText("w", 1) }
        composeRule.waitForIdle()
        onUi { input.setComposingText("wo", 1) }
        composeRule.waitForIdle()
        onUi { input.setComposingText("word", 1) }
        composeRule.waitForIdle()
        assertEquals(TextRange(0, 4), state.composition)
        assertEquals("word", document.text)
    }

    @Test
    fun `the caret stays on its character when a remote change arrives`() {
        startEditor("Hello world", 6)
        remote(">> Hello world")
        composeRule.waitForIdle()
        assertEquals(">> Hello world", state.text)
        assertEquals(TextRange(9), state.selection)
    }

    @Test
    fun `an autocorrect replacing a word keeps a concurrent edit in the same line`() {
        val input = startEditor("teh cat", 3)
        onUi {
            input.setComposingRegion(0, 3)
            document.remoteEdit("teh cats", controller)
            input.setComposingText("the", 1)
            input.finishComposingText()
        }
        composeRule.waitForIdle()
        assertEquals("the cats", document.text)
        assertEquals("the cats", state.text)
    }

    @Test
    fun `deletes of the keyboard reach the document one by one`() {
        val input = startEditor("Hello", 5)
        onUi {
            input.deleteSurroundingText(1, 0)
            input.deleteSurroundingText(1, 0)
        }
        assertEquals("Hel", document.text)
        assertEquals(TextRange(3), state.selection)
    }

    @Test
    fun `typing works again after leaving and entering the edit mode`() {
        val input = startEditor("Hello", 5)
        onUi { input.commitText("!", 1) }
        assertEquals("Hello!", document.text)

        editMode = false
        composeRule.waitForIdle()
        onUi { input.commitText("?", 1) }
        composeRule.waitForIdle()
        assertEquals("Hello!", document.text)
        assertEquals("Hello!", state.text)

        editMode = true
        composeRule.waitForIdle()
        val again = focusEditor()
        onUi { again.commitText("+", 1) }
        composeRule.waitForIdle()
        assertEquals("Hello!+", document.text)
        assertEquals("Hello!+", state.text)
    }
}
