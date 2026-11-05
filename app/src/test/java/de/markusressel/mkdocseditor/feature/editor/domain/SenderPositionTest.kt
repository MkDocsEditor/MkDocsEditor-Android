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

    private fun peer(account: String?, head: Int?, lastActiveAt: String? = null, viewport: RemoteViewport? = null) = RemotePeer(
        clientId = "c",
        username = "u",
        displayName = null,
        color = null,
        selections = listOfNotNull(head?.let { RemoteSelection(it, it) }),
        accountId = account,
        lastActiveAt = lastActiveAt,
        viewport = viewport,
    )

    private fun ghost(account: String, head: Int?, viewport: RemoteViewport? = null) = RemoteGhost(
        accountId = account,
        username = "u",
        displayName = null,
        color = null,
        selections = listOfNotNull(head?.let { RemoteSelection(it, it) }),
        viewport = viewport,
        remainingMs = 1000,
    )

    @Test
    fun `follows the connection that moved last`() {
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
    fun `a ghost is followed until the account is back`() {
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
    fun `an account counts as present while it is connected or a ghost`() {
        assertTrue(isPresent(listOf(peer("sender", null)), emptyList(), "sender"))
        assertTrue(isPresent(emptyList(), listOf(ghost("sender", 1)), "sender"))
        assertFalse(isPresent(listOf(peer("other", 1)), listOf(ghost("other", 1)), "sender"))
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
