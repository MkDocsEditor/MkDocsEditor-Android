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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.EditorField
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorLayout
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.EditorFieldValue
import de.markusressel.mkdocsrestclient.sync.automerge.EditorTextBinding
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
 * The real text field with the real [InputConnection] of Compose, the way an input method talks
 * to it, connected to a real Automerge document with [EditorTextBinding].
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
        override fun splicesSince(version: DocumentVersion, text: String) = automerge.splicesSince(version, text)

        /** what a sync message from another user does */
        fun remoteEdit(newText: String, binding: EditorTextBinding) {
            val other = automerge.document.fork()
            val otherDocument = AutomergeTextDocument.fromDocument(other)
            otherDocument.applyLocalText(newText)
            val splices = automerge.merge(otherDocument)
            binding.onDocumentChanged(splices)
        }
    }

    private var time = 1_000_000L
    private lateinit var document: TestDocument
    private lateinit var binding: EditorTextBinding
    private var field by mutableStateOf(EditorField())
    private lateinit var view: View

    private fun syncField() {
        val value = binding.value
        field = EditorField(
            TextFieldValue(
                value.text,
                TextRange(value.selectionStart, value.selectionEnd),
                if (value.isComposing) TextRange(value.compositionStart!!, value.compositionEnd!!) else null,
            ),
            binding.valueId,
        )
    }

    /** starts the editor with the caret at [caret] and returns its input connection */
    private fun startEditor(text: String, caret: Int): InputConnection {
        document = TestDocument(text)
        binding = EditorTextBinding(document) { time }
        binding.setSelection(caret, caret)
        syncField()
        composeRule.setContent {
            view = LocalView.current
            MaterialTheme {
                CodeEditorLayout(
                    modifier = Modifier.size(400.dp, 400.dp),
                    field = field,
                    onTextChanged = {
                        binding.onFieldChanged(
                            EditorFieldValue(
                                it.text, it.selection.start, it.selection.end, it.composition?.min, it.composition?.max,
                            )
                        )
                        syncField()
                    },
                    onValueShown = binding::onValueShown,
                    readOnly = false,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
            .performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.waitForIdle()
        return connection()
    }

    private fun connection(): InputConnection = assertNotNull(view.onCreateInputConnection(EditorInfo()))

    private fun remote(newText: String) {
        composeRule.runOnUiThread {
            document.remoteEdit(newText, binding)
            syncField()
        }
    }

    private fun onUi(block: () -> Unit) = composeRule.runOnUiThread(block)

    @Test
    fun `a key press in the same frame as a remote change keeps both`() {
        val input = startEditor("Hello", 5)
        composeRule.mainClock.autoAdvance = false

        // no frame between the remote change and the key press
        onUi {
            document.remoteEdit("HelloX", binding)
            syncField()
            input.commitText("Y", 1)
        }
        composeRule.mainClock.advanceTimeByFrame()
        time += EditorTextBinding.TYPING_IDLE_MS
        onUi { binding.flush(); syncField() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertTrue(document.text.contains("X"), document.text)
        assertTrue(document.text.contains("Y"), document.text)
        assertEquals(document.text, field.value.text)
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
        composeRule.waitForIdle()
        assertEquals("abcd", document.text)
        assertEquals("abcd", field.value.text)
        assertEquals(TextRange(4), field.value.selection)
    }

    @Test
    fun `a composition survives a remote change and every state reaches the document`() {
        val input = startEditor("Text ", 5)

        onUi { input.setComposingText("M", 1) }
        composeRule.waitForIdle()
        assertEquals(TextRange(5, 6), field.value.composition)

        // the same input connection stays in use: nothing restarted the input method
        remote("REMOTE Text M")
        composeRule.waitForIdle()
        assertEquals("Text M", field.value.text)
        assertEquals(TextRange(5, 6), field.value.composition)

        onUi { input.setComposingText("Mü", 1) }
        composeRule.waitForIdle()
        assertEquals("REMOTE Text Mü", document.text)
        assertEquals(TextRange(5, 7), field.value.composition)

        onUi { input.setComposingText("Müller", 1) }
        composeRule.waitForIdle()
        assertEquals(TextRange(5, 11), field.value.composition)

        onUi { input.finishComposingText() }
        composeRule.waitForIdle()
        assertEquals("REMOTE Text Müller", document.text)

        time += EditorTextBinding.TYPING_IDLE_MS
        onUi { binding.flush(); syncField() }
        composeRule.waitForIdle()
        assertEquals("REMOTE Text Müller", field.value.text)
        assertEquals(TextRange("REMOTE Text Müller".length), field.value.selection)
        assertNull(field.value.composition)
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
        assertEquals(TextRange(0, 4), field.value.composition)
        assertEquals("word", document.text)
    }

    @Test
    fun `the caret stays on its character when a remote change arrives`() {
        startEditor("Hello world", 6)
        remote(">> Hello world")
        time += 1000
        onUi { binding.flush(); syncField() }
        composeRule.waitForIdle()
        assertEquals(">> Hello world", field.value.text)
        assertEquals(TextRange(9), field.value.selection)
    }
}
