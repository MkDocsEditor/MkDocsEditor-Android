package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SenderPositionTest {

    private fun peer(account: String?, head: Int?, lastActiveAt: String? = null) = RemotePeer(
        clientId = "c",
        username = "u",
        displayName = null,
        color = null,
        selections = listOfNotNull(head?.let { RemoteSelection(it, it) }),
        accountId = account,
        lastActiveAt = lastActiveAt,
    )

    @Test
    fun `finds the sender among the others`() {
        assertEquals(7, senderPosition(listOf(peer("other", 1), peer("sender", 7)), emptyMap(), "sender"))
    }

    @Test
    fun `takes the connection that moved last`() {
        val peers = listOf(
            peer("sender", 1, "2026-11-05T10:00:00Z"),
            peer("sender", 2, "2026-11-05T10:00:05Z"),
            peer("sender", 3),
        )
        assertEquals(2, senderPosition(peers, emptyMap(), "sender"))
    }

    @Test
    fun `a connection without a cursor does not count`() {
        val peers = listOf(peer("sender", null, "2026-11-05T11:00:00Z"), peer("sender", 4, "2026-11-05T10:00:00Z"))
        assertEquals(4, senderPosition(peers, emptyMap(), "sender"))
    }

    @Test
    fun `no position while the sender is not there`() {
        assertNull(senderPosition(listOf(peer("other", 1), peer("sender", null)), emptyMap(), "sender"))
    }

    @Test
    fun `a sender who is online wins over where they last were`() {
        assertEquals(7, senderPosition(listOf(peer("sender", 7)), mapOf("sender" to RemoteSelection(3, 3)), "sender"))
    }

    @Test
    fun `a sender who has left is found where they last were`() {
        assertEquals(3, senderPosition(listOf(peer("other", 1)), mapOf("sender" to RemoteSelection(3, 3)), "sender"))
    }

    @Test
    fun `a sender online without a cursor is found where they last were`() {
        assertEquals(3, senderPosition(listOf(peer("sender", null)), mapOf("sender" to RemoteSelection(3, 3)), "sender"))
    }
}
