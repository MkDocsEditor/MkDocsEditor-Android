package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePosition

/** Where to look in order to see what another user sees */
internal sealed interface FollowTarget {
    data class Position(val position: Int) : FollowTarget

    /** the text they look at, as the indices of its first and last character */
    data class Range(val top: Int, val bottom: Int) : FollowTarget
}

/**
 * The connections of an instance, the one that moved last first: an old one can linger on a half-open socket. An ISO instant
 * in UTC sorts by time, and a connection without a time counts as the oldest.
 */
private fun List<RemotePeer>.ofInstance(instanceId: String) =
    filter { it.instanceId == instanceId }.sortedByDescending { it.lastActiveAt.orEmpty() }

/** whether the instance is here, which a ghost still counts as */
internal fun isPresent(peers: List<RemotePeer>, ghosts: List<RemoteGhost>, instanceId: String): Boolean =
    peers.any { it.instanceId == instanceId } || ghosts.any { it.instanceId == instanceId }

/**
 * Who a join link follows: the instance it names, else the instance of the sender that was active last, never one of
 * their AI agents. A ghost with more time left has left later.
 *
 * @return null if the sender is fully away
 */
internal fun joinFollowInstance(
    peers: List<RemotePeer>,
    ghosts: List<RemoteGhost>,
    from: String,
    instance: String?,
): String? {
    if (instance != null && isPresent(peers, ghosts, instance)) return instance
    // an agent of the sender has no cursor or viewport, and while it edits it is the one that was active last
    return peers.filter { it.accountId == from && it.agent == null }.maxByOrNull { it.lastActiveAt.orEmpty() }?.instanceId
        ?: ghosts.filter { it.accountId == from && it.agent == null }.maxByOrNull { it.remainingMs }?.instanceId
}

/** @return null while there is nothing to look at: no connection of the instance has a viewport or a cursor */
internal fun followTarget(peers: List<RemotePeer>, ghosts: List<RemoteGhost>, instanceId: String): FollowTarget? {
    peers.ofInstance(instanceId).forEach { peer ->
        peer.viewport?.let { return FollowTarget.Range(it.top, it.bottom) }
        peer.selections.firstOrNull()?.let { return FollowTarget.Position(it.head) }
    }
    val ghost = ghosts.firstOrNull { it.instanceId == instanceId } ?: return null
    ghost.viewport?.let { return FollowTarget.Range(it.top, it.bottom) }
    return ghost.selections.firstOrNull()?.let { FollowTarget.Position(it.head) }
}

/**
 * Where a sender of a join link who is not here anymore last was, as a text index
 *
 * @param lastPositions where accounts last were, by account
 */
internal fun lastSenderPosition(lastPositions: Map<String, RemotePosition>, from: String): Int? =
    lastPositions[from]?.let { it.selection?.head ?: it.viewport?.top }

/** Same in the web client */
internal const val FOLLOW_PAUSE_MIN_MS = 5_000L
private const val FOLLOW_PAUSE_MAX_MS = 40_000L
private const val FOLLOW_RESUME_SETTLE_MS = 10_000L

/**
 * How long a manual move pauses following. A move right after following resumed means the resume came too early, so the
 * pause gets longer each time.
 *
 * @param currentMs the pause of the last manual move
 * @param sinceResumeMs how long following has been running again, null if it never paused
 */
internal fun nextFollowPauseMs(currentMs: Long, sinceResumeMs: Long?): Long =
    if (sinceResumeMs != null && sinceResumeMs < FOLLOW_RESUME_SETTLE_MS) {
        (currentMs * 2).coerceAtMost(FOLLOW_PAUSE_MAX_MS)
    } else {
        FOLLOW_PAUSE_MIN_MS
    }
