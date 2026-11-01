package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.EditorField
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorLayout
import de.markusressel.mkdocseditor.feature.editor.ui.compose.LaidOutPeers
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The name label of another user must not jump while that user types: the peer positions
 * belong to the new text, and must never be drawn on the layout of the old text.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemotePeersOverlayTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun peer(head: Int) = RemotePeer(
        clientId = "c1",
        username = "alice",
        displayName = "alice",
        color = "#ff0000",
        selections = listOf(RemoteSelection(head, head)),
    )

    @Test
    fun `the label stays at the end of the line while the other user types there`() {
        var text by mutableStateOf("hello\nworld")
        var peers by mutableStateOf(listOf(peer(5)))

        composeRule.setContent {
            MaterialTheme {
                CodeEditorLayout(
                    modifier = Modifier.size(400.dp, 400.dp),
                    field = EditorField(TextFieldValue(text, TextRange.Zero), remotePeers = peers),
                    onTextChanged = {},
                    onValueShown = {},
                    readOnly = true,
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false

        // top left corner of the name label in px; useUnmergedTree includes its placement
        val positions = mutableListOf<Offset>()
        fun record() {
            positions += composeRule.onNodeWithText("alice", useUnmergedTree = true).fetchSemanticsNode().positionInRoot
        }
        record()

        // alice types "!!!" at the end of the first line, one change at a time; the session
        // updates the text and the resolved peers in the same main thread message
        repeat(3) {
            val head = text.indexOf('\n')
            text = text.substring(0, head) + "!" + text.substring(head)
            peers = listOf(peer(head + 1))
            repeat(3) {
                composeRule.mainClock.advanceTimeByFrame()
                record()
            }
        }

        assertEquals(1, positions.map { it.y }.distinct().size, "the label changed lines: $positions")
        assertTrue(positions.zipWithNext().all { (a, b) -> b.x >= a.x }, "the label moved back: $positions")
        assertTrue(positions.last().x > positions.first().x, "the label did not follow: $positions")
    }

    @Test
    fun `keeps the peers of the laid out text until the new text is laid out`() {
        val laidOutPeers = LaidOutPeers()
        val before = listOf(peer(5))
        val after = listOf(peer(6))
        assertEquals(before, laidOutPeers.select(laidOutText = "hello", text = "hello", peers = before))
        assertEquals(before, laidOutPeers.select(laidOutText = "hello", text = "hello!", peers = after))
        assertEquals(after, laidOutPeers.select(laidOutText = "hello!", text = "hello!", peers = after))
    }

    @Test
    fun `draws nothing before the first matching layout`() {
        assertEquals(emptyList(), LaidOutPeers().select(laidOutText = "", text = "hello", peers = listOf(peer(1))))
    }
}
