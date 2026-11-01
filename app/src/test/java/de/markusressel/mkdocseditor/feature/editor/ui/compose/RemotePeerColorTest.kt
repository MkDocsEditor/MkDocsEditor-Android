package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorPalette
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.contrastRatio
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemotePeerColorTest {

    @Test
    fun `parsePeerColor parses rgb and argb colors`() {
        assertEquals(Color(0xFF123456), parsePeerColor("#123456"))
        assertEquals(Color(0x80123456), parsePeerColor("#80123456"))
        assertEquals(Color(0xFFABCDEF), parsePeerColor("abcdef"))
    }

    @Test
    fun `parsePeerColor rejects invalid colors`() {
        assertNull(parsePeerColor(null))
        assertNull(parsePeerColor(""))
        assertNull(parsePeerColor("#123"))
        assertNull(parsePeerColor("#12345G"))
        assertNull(parsePeerColor("red"))
        assertNull(parsePeerColor("-12345"))
    }

    @Test
    fun `peers without a valid color get a stable color per username`() {
        val alice = peer(username = "alice", color = null)
        val aliceOnOtherDevice = peer(username = "alice", color = "invalid")

        assertEquals(alice.composeColor, aliceOnOtherDevice.composeColor)
        assertEquals(Color(0xFF123456), peer(username = "alice", color = "#123456").composeColor)
    }

    @Test
    fun `name prefers the display name`() {
        assertEquals("Alice", peer(username = "alice", displayName = "Alice").name)
        assertEquals("alice", peer(username = "alice", displayName = " ").name)
        assertEquals("alice", peer(username = "alice", displayName = null).name)
    }

    @Test
    fun `peer colors stay visible on both editor backgrounds`() {
        val backgrounds = listOf(EditorPalette.Dark.background, EditorPalette.Light.background)
        val userColors = listOf(Color(0xFF000080), Color(0xFFFFFF00), Color(0xFF1A1C18), Color(0xFFFDFDF5))
        (fallbackPeerColors + userColors).forEach { color ->
            backgrounds.forEach { background ->
                val readable = color.readableOn(background)
                assertTrue(
                    contrastRatio(readable, background) >= 3.0,
                    "$color on $background: ${contrastRatio(readable, background)}",
                )
            }
        }
    }

    @Test
    fun `fallback colors need no adjustment`() {
        listOf(EditorPalette.Dark.background, EditorPalette.Light.background).forEach { background ->
            fallbackPeerColors.forEach { assertEquals(it, it.readableOn(background)) }
        }
    }

    @Test
    fun `labels use the more readable of black and white`() {
        assertEquals(Color.Black, labelTextColorOn(Color(0xFFE4C070)))
        assertEquals(Color.White, labelTextColorOn(Color(0xFF000080)))
        (fallbackPeerColors).forEach {
            assertTrue(contrastRatio(labelTextColorOn(it), it) >= 4.5, "label on $it")
        }
    }

    private fun peer(
        username: String,
        displayName: String? = null,
        color: String? = null,
    ) = RemotePeer(
        clientId = "client",
        username = username,
        displayName = displayName,
        color = color,
        selections = emptyList(),
    )
}
