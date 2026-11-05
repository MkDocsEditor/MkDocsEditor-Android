package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePosition
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteViewport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SenderPositionTest {

    /** @param instance the account has one instance unless a test says otherwise */
    private fun peer(
        account: String,
        head: Int?,
        lastActiveAt: String? = null,
        viewport: RemoteViewport? = null,
        instance: String = account,
    ) = RemotePeer(
        clientId = "c",
        username = "u",
        displayName = null,
        color = null,
        selections = listOfNotNull(head?.let { RemoteSelection(it, it) }),
        accountId = account,
        lastActiveAt = lastActiveAt,
        viewport = viewport,
        instanceId = instance,
    )

    private fun ghost(
        account: String,
        head: Int?,
        viewport: RemoteViewport? = null,
        instance: String = account,
        remainingMs: Long = 1000,
    ) = RemoteGhost(
        instanceId = instance,
        accountId = account,
        username = "u",
        displayName = null,
        color = null,
        selections = listOfNotNull(head?.let { RemoteSelection(it, it) }),
        viewport = viewport,
        remainingMs = remainingMs,
    )

    @Test
    fun `follows the connection of the instance that moved last`() {
        val peers = listOf(
            peer("sender", 1, "2026-11-05T10:00:00Z"),
            peer("sender", 2, "2026-11-05T10:00:05Z"),
            peer("other", 9, "2026-11-05T11:00:00Z"),
        )
        assertEquals(FollowTarget.Position(2), followTarget(peers, emptyList(), "sender"))
    }

    @Test
    fun `the viewport decides over the cursor`() {
        val peers = listOf(peer("sender", 1, viewport = RemoteViewport(10, 20)))
        assertEquals(FollowTarget.Range(10, 20), followTarget(peers, emptyList(), "sender"))
    }

    @Test
    fun `a connection without a position is skipped`() {
        val peers = listOf(peer("sender", null, "2026-11-05T11:00:00Z"), peer("sender", 4, "2026-11-05T10:00:00Z"))
        assertEquals(FollowTarget.Position(4), followTarget(peers, emptyList(), "sender"))
    }

    @Test
    fun `a ghost is followed until the instance is back`() {
        assertEquals(FollowTarget.Position(5), followTarget(emptyList(), listOf(ghost("sender", 5)), "sender"))
        assertEquals(
            FollowTarget.Range(1, 2),
            followTarget(listOf(peer("sender", null, viewport = RemoteViewport(1, 2))), listOf(ghost("sender", 5)), "sender"),
        )
    }

    @Test
    fun `nothing to follow without a position`() {
        assertNull(followTarget(listOf(peer("sender", null)), emptyList(), "sender"))
    }

    @Test
    fun `an instance counts as present while it is connected or a ghost`() {
        assertTrue(isPresent(listOf(peer("sender", null)), emptyList(), "sender"))
        assertTrue(isPresent(emptyList(), listOf(ghost("sender", 1)), "sender"))
        assertFalse(isPresent(listOf(peer("other", 1)), listOf(ghost("other", 1)), "sender"))
    }

    @Test
    fun `following stays on one instance of an account`() {
        val peers = listOf(peer("sender", 1, instance = "phone"), peer("sender", 2, "2026-11-05T11:00:00Z", instance = "laptop"))
        assertEquals(FollowTarget.Position(1), followTarget(peers, emptyList(), "phone"))
        assertFalse(isPresent(peers, emptyList(), "tablet"))
    }

    @Test
    fun `a join link follows the instance it names, also as a ghost`() {
        val peers = listOf(peer("sender", 1, "2026-11-05T11:00:00Z", instance = "laptop"))
        val ghosts = listOf(ghost("sender", 2, instance = "phone"))
        assertEquals("laptop", joinFollowInstance(peers, ghosts, "sender", "laptop"))
        assertEquals("phone", joinFollowInstance(peers, ghosts, "sender", "phone"))
    }

    @Test
    fun `a join link without its instance follows the one that was active last`() {
        val peers = listOf(
            peer("sender", 1, "2026-11-05T10:00:00Z", instance = "old"),
            peer("sender", 2, "2026-11-05T11:00:00Z", instance = "new"),
            peer("other", 3, "2026-11-05T12:00:00Z", instance = "other"),
        )
        assertEquals("new", joinFollowInstance(peers, emptyList(), "sender", "gone"))
        assertEquals("new", joinFollowInstance(peers, emptyList(), "sender", null))
    }

    @Test
    fun `a connected instance goes before a ghost, and of the ghosts the one that left last`() {
        val ghosts = listOf(
            ghost("sender", 1, instance = "earlier", remainingMs = 1000),
            ghost("sender", 2, instance = "later", remainingMs = 5000),
        )
        assertEquals("later", joinFollowInstance(emptyList(), ghosts, "sender", null))
        assertEquals("laptop", joinFollowInstance(listOf(peer("sender", 1, instance = "laptop")), ghosts, "sender", null))
    }

    @Test
    fun `a join link follows nobody when the sender is fully away`() {
        assertNull(joinFollowInstance(listOf(peer("other", 1)), listOf(ghost("other", 1)), "sender", "phone"))
    }

    @Test
    fun `a sender who has left is found where they last were`() {
        val positions = mapOf(
            "cursor" to RemotePosition(RemoteSelection(3, 3), RemoteViewport(1, 2)),
            "reader" to RemotePosition(null, RemoteViewport(7, 9)),
        )
        assertEquals(3, lastSenderPosition(positions, "cursor"))
        assertEquals(7, lastSenderPosition(positions, "reader"))
        assertNull(lastSenderPosition(positions, "unknown"))
    }

    @Test
    fun `the first manual move pauses following for the shortest time`() {
        assertEquals(5_000L, nextFollowPauseMs(FOLLOW_PAUSE_MIN_MS, sinceResumeMs = null))
    }

    @Test
    fun `moving again right after the resume doubles the pause up to a limit`() {
        assertEquals(10_000L, nextFollowPauseMs(5_000L, sinceResumeMs = 2_000L))
        assertEquals(20_000L, nextFollowPauseMs(10_000L, sinceResumeMs = 9_999L))
        assertEquals(40_000L, nextFollowPauseMs(20_000L, sinceResumeMs = 0L))
        assertEquals(40_000L, nextFollowPauseMs(40_000L, sinceResumeMs = 0L))
    }

    @Test
    fun `moving long after the resume starts over`() {
        assertEquals(5_000L, nextFollowPauseMs(40_000L, sinceResumeMs = 10_000L))
    }
}
