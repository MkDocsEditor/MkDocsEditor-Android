package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorLayout
import de.markusressel.mkdocseditor.feature.editor.ui.compose.EditorSettings
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
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
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemoteEditsFadeInTest {

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
            val other = AutomergeTextDocument.fromDocument(automerge.document.fork())
            other.applyLocalText(newText)
            val splices = automerge.merge(other)
            controller.onRemoteChange(text, splices, author = "jane")
        }
    }

    private val document = TestDocument("a\n")
    private val controller = EditorTextController(editModeActive = { true }).also { it.bind(document) }

    private fun showEditor(settings: EditorSettings) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                CodeEditorLayout(
                    modifier = Modifier.size(300.dp).testTag("editor"),
                    state = controller.editorState,
                    settings = settings,
                    readOnly = true,
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** how many pixels of the editor are not the color of its background */
    private fun drawnPixels(): Int {
        val image = composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
        val background = image[image.width - 1, image.height - 1]
        var count = 0
        for (x in 0 until image.width) for (y in 0 until image.height) if (image[x, y] != background) count++
        return count
    }

    /** another user writes a line, and the user types in the line above right after */
    private fun remoteEditThenTyping() = composeRule.runOnIdle {
        document.remoteEdit("a\nMMMM WWWW", controller)
        controller.select(1, 1)
        controller.editorState.replaceSelection("!")
        // a paused clock sends no notifications about changed state on its own
        Snapshot.sendApplyNotifications()
    }

    @Test
    fun `text of others fades in, and typing does not end that`() {
        showEditor(EditorSettings())
        remoteEditThenTyping()
        assertEquals("a!\nMMMM WWWW", document.text)

        composeRule.mainClock.advanceTimeBy(50)
        val fading = drawnPixels()
        composeRule.mainClock.advanceTimeBy(1_000)
        val shown = drawnPixels()
        assertTrue(fading < shown, "the text of jane is still fading in after the typing, $fading < $shown")
    }

    @Test
    fun `without the setting, text of others shows at once`() {
        showEditor(EditorSettings(animateRemoteEdits = false))
        remoteEditThenTyping()

        composeRule.mainClock.advanceTimeBy(50)
        val early = drawnPixels()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(early, drawnPixels())
    }
}
