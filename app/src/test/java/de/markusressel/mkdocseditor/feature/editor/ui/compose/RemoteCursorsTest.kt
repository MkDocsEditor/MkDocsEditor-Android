package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteCursorsTest {

    private val background = Color(0xFF1A1C18)
    private val foreground = Color(0xFFE2E3DC)

    private fun peer(selection: RemoteSelection = RemoteSelection(2, 5)) = RemotePeer(
        clientId = "connection-1",
        username = "alice",
        displayName = "Alice",
        color = "#3d8fe0",
        selections = listOf(selection),
        instanceId = "phone",
    )

    private fun ghost() = RemoteGhost(
        instanceId = "phone",
        accountId = "a1",
        username = "alice",
        displayName = "Alice",
        color = "#3d8fe0",
        selections = listOf(RemoteSelection(2, 5)),
        viewport = null,
        remainingMs = 5_000,
    ).asPeer()

    @Test
    fun `a user who leaves keeps the cursor and only turns faint`() {
        val live = remoteCursorsOf(listOf(peer()), emptyList(), background, foreground).cursors.single()
        val left = remoteCursorsOf(emptyList(), listOf(ghost()), background, foreground).cursors.single()

        assertEquals(live.id, left.id)
        assertEquals(1f, live.caretColor.alpha)
        assertEquals(0.4f, left.caretColor.alpha, 0.01f)
        assertTrue(left.selectionColor.alpha < live.selectionColor.alpha)
    }

    @Test
    fun `a connection that is back wins over its ghost`() {
        val cursors = remoteCursorsOf(listOf(peer()), listOf(ghost()), background, foreground)

        assertEquals(1f, cursors.cursors.single().caretColor.alpha)
        assertEquals("connection-1", cursors.peerOf(cursors.cursors.single())?.first?.clientId)
    }

    @Test
    fun `the cursor is at the head of a selection made backwards`() {
        val cursor = remoteCursorsOf(listOf(peer(RemoteSelection(anchor = 9, head = 4))), emptyList(), background, foreground)
            .cursors.single()

        assertEquals(listOf(TextRange(9, 4)), cursor.selections)
    }

    @Test
    fun `a user without a selection has no cursor`() {
        val cursors = remoteCursorsOf(listOf(peer().copy(selections = emptyList())), emptyList(), background, foreground)

        assertEquals(emptyList(), cursors.cursors)
    }
}
