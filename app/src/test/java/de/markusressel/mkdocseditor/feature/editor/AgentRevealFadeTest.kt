package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import kotlin.math.abs
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
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
import de.markusressel.mkdocsrestclient.sync.automerge.AgentReveal
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

/** The text of an agent is typed out, and each piece fades in like the text of others, see docs/agent-presence.md */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentRevealFadeTest {

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

        /** a live change of an agent, announced with agent-change */
        fun agentEdit(newText: String, controller: EditorTextController) {
            val other = AutomergeTextDocument.fromDocument(automerge.document.fork())
            other.applyLocalText(newText)
            val start = text.length
            val splices = automerge.merge(other)
            val reveal = AgentReveal("agent-1", "i1", listOf(start until text.length))
            controller.onRemoteChange(text, splices, author = "markus", agentReveals = listOf(reveal))
        }
    }

    private val document = TestDocument("a\n")
    private val controller = EditorTextController(editModeActive = { true }).also {
        it.bind(document)
        it.revealAllowed = true
    }

    private fun showEditor() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                CodeEditorLayout(
                    modifier = Modifier.size(300.dp).testTag("editor"),
                    state = controller.editorState,
                    settings = EditorSettings(),
                    readOnly = true,
                )
                // as the screen runs them
                controller.agentReveals.value.values.forEach { reveal ->
                    key(reveal) { LaunchedEffect(reveal) { reveal.keepRevealing() } }
                }
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
    }

    /** how much ink the editor draws: the difference of all its pixels to its background, so a fade shows in it */
    private fun drawnPixels(): Int {
        val image = composeRule.onNodeWithTag("editor").captureToImage().toPixelMap()
        val background = image[image.width - 1, image.height - 1]
        var ink = 0f
        for (x in 0 until image.width) for (y in 0 until image.height) {
            val pixel = image[x, y]
            ink += abs(pixel.red - background.red) + abs(pixel.green - background.green) + abs(pixel.blue - background.blue)
        }
        return (ink * 100).toInt()
    }

    private fun update() = Snapshot.sendApplyNotifications()

    @Test
    fun `an agent's text is typed out, and each piece fades in when it is revealed`() {
        showEditor()
        composeRule.runOnIdle {
            document.agentEdit("a\nMMMM WWWW", controller)
            update()
        }
        composeRule.mainClock.advanceTimeByFrame()
        val started = drawnPixels()
        // 9 characters at 60 a second are all revealed after 150 ms, the last ones still fade in
        composeRule.mainClock.advanceTimeBy(200)
        assertEquals(emptyList(), controller.editorState.rangeStyles)
        val fading = drawnPixels()
        composeRule.mainClock.advanceTimeBy(2_000)
        val shown = drawnPixels()

        assertTrue(started < fading, "the text was not typed out, $started < $fading")
        assertTrue(fading < shown, "the revealed text does not fade in, $fading < $shown")
    }

    @Test
    fun `without the reveal, the text of an agent fades in as a whole`() {
        controller.revealAllowed = false
        showEditor()
        composeRule.runOnIdle {
            document.agentEdit("a\nMMMM WWWW", controller)
            update()
        }
        composeRule.mainClock.advanceTimeBy(50)
        val fading = drawnPixels()
        composeRule.mainClock.advanceTimeBy(1_000)
        assertTrue(fading < drawnPixels())
        assertEquals(emptyList(), controller.editorState.rangeStyles)
    }
}
