package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection

/**
 * Where the sender of a join link is right now, as a text index.
 *
 * The sender may have several connections, the one that moved last is the one they are using.
 * An ISO instant in UTC sorts by time, and a connection without a time counts as the oldest.
 * A sender who has left, for example to send the link, is found where they last were.
 *
 * @param lastPositions where accounts last had a cursor, by account
 * @return null while the position of the sender is not known
 */
internal fun senderPosition(peers: List<RemotePeer>, lastPositions: Map<String, RemoteSelection>, from: String): Int? = peers
    .filter { it.accountId == from && it.selections.isNotEmpty() }
    .maxByOrNull { it.lastActiveAt.orEmpty() }
    ?.selections?.first()?.head
    ?: lastPositions[from]?.head
