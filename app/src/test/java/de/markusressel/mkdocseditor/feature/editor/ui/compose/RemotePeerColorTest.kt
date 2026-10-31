package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
