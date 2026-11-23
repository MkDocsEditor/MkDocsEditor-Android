package de.markusressel.mkdocsrestclient.sync.automerge

import com.squareup.moshi.Moshi
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.IndexSelectionMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.IndexViewportMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.LocalPresenceMessage
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The position of this client as code point indexes (`at`), for servers with `presence-indexes`, see "Indexes" in
 * docs/sync-protocol.md of the backend.
 */
class PresenceIndexesTest {

    private val adapter = Moshi.Builder().build().adapter(LocalPresenceMessage::class.java)

    private fun connected(text: String): Pair<FakeSyncServer, DocumentSyncSession> {
        val server = FakeSyncServer(text)
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), object : DocumentSyncSession.Listener {})
        server.connect(session)
        server.pump()
        return server to session
    }

    private fun FakeSyncServer.lastPresence(): LocalPresenceMessage {
        pump()
        return assertNotNull(adapter.fromJson(receivedPresences.last()))
    }

    @Test
    fun `without the feature, presence carries no indexes`() {
        val (server, session) = connected("hello")

        session.updateLocalPresence(1 to 3, 0 to 4)

        assertNull(server.lastPresence().at)
    }

    @Test
    fun `the indexes count code points of the text at the heads of the local document`() {
        val (server, session) = connected("a😀b\n😀cd")
        session.sendPresenceIndexes = true

        // UTF-16: 'b' is at 3, the second emoji at 5 and 6, 'd' at 8
        session.updateLocalPresence(3 to 7, 0 to 8)

        val at = assertNotNull(server.lastPresence().at)
        assertEquals(session.version!!.headsHex.joinToString("."), at.revision)
        assertEquals(listOf(IndexSelectionMessage(anchor = 2, head = 5)), at.selections)
        assertEquals(IndexViewportMessage(top = 0, bottom = 6), at.viewport)
    }

    @Test
    fun `a position inside an emoji means the emoji`() {
        val (server, session) = connected("a😀b")
        session.sendPresenceIndexes = true

        // between the two halves of the emoji, and the last visible character its second half
        session.updateLocalPresence(2 to 2, 0 to 2)

        val at = assertNotNull(server.lastPresence().at)
        assertEquals(listOf(IndexSelectionMessage(1, 1)), at.selections)
        assertEquals(IndexViewportMessage(0, 1), at.viewport)
    }

    @Test
    fun `turning the feature on sends the position at once`() {
        val (server, session) = connected("hello")
        session.updateLocalPresence(5 to 5, null)
        server.pump()
        val before = server.receivedPresences.size

        session.sendPresenceIndexes = true
        server.pump()

        assertTrue(server.receivedPresences.size > before)
        assertEquals(listOf(IndexSelectionMessage(5, 5)), assertNotNull(server.lastPresence().at).selections)
    }

    @Test
    fun `every presence carries the whole position, also when only the cursor moved`() {
        val (server, session) = connected("hello world")
        session.sendPresenceIndexes = true
        session.updateLocalPresence(0 to 0, 0 to 10)

        session.updateLocalSelection(6, 6)

        val at = assertNotNull(server.lastPresence().at)
        assertEquals(listOf(IndexSelectionMessage(6, 6)), at.selections)
        assertEquals(IndexViewportMessage(0, 10), at.viewport)
    }

    @Test
    fun `no cursor is an empty list, and an empty text is seen from 0 to 0`() {
        val (server, session) = connected("")
        session.sendPresenceIndexes = true

        session.updateLocalPresence(null to null, 0 to -1)

        val at = assertNotNull(server.lastPresence().at)
        assertEquals(emptyList(), at.selections)
        assertEquals(IndexViewportMessage(0, 0), at.viewport)
    }

    @Test
    fun `a local edit moves the revision to the new heads`() {
        val (server, session) = connected("hello")
        session.sendPresenceIndexes = true
        session.updateLocalSelection(5, 5)
        val before = assertNotNull(server.lastPresence().at).revision

        session.applyLocalEdit(session.version!!, session.text!!, listOf(TextSplice(5, 0, "!")))
        session.updateLocalSelection(6, 6)

        val at = assertNotNull(server.lastPresence().at)
        assertNotEquals(before, at.revision)
        assertEquals(session.version!!.headsHex.joinToString("."), at.revision)
        assertEquals(listOf(IndexSelectionMessage(6, 6)), at.selections)
    }
}
