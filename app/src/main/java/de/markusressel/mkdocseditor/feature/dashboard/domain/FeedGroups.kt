package de.markusressel.mkdocseditor.feature.dashboard.domain

import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind

/**
 * A row of the changes on the dashboard: one entry, or the edits of one document.
 *
 * @param entries newest first, never empty. More than one only for edits of the same path.
 */
internal data class FeedRow(val entries: List<FeedEntryModel>) {
    val newest: FeedEntryModel get() = entries.first()
    val isGroup: Boolean get() = entries.size > 1

    /** everyone who edited, in the order they first did, see [groupFeed] */
    val authors: List<FeedAuthorModel>
        get() = entries.asReversed().flatMap { it.authors }.distinctBy { it.accountId ?: ":${it.displayName}" }

    /** the headings of all edits, those of the newest edit first */
    val headings: List<String>
        get() = entries.flatMap { it.summary?.headings.orEmpty() }.distinct()
}

/**
 * Folds the edits of a document into one row, so that a page edited often does not fill the list. The web client
 * groups by the same rule.
 *
 * Walks [entries] newest first. An `edited` entry joins the group open for its path, or starts one where it is. Any
 * other entry is a row of its own, and ends the groups of its path and, after a move, of the old path: edits from
 * before it start a new group. A group stays where its newest entry is, so older pages only add to it.
 */
internal fun groupFeed(entries: List<FeedEntryModel>): List<FeedRow> {
    val rows = mutableListOf<MutableList<FeedEntryModel>>()
    val open = mutableMapOf<String, MutableList<FeedEntryModel>>()
    for (entry in entries) {
        if (entry.kind == FeedKind.EDITED) {
            open[entry.path]?.let { it += entry } ?: mutableListOf(entry).also {
                rows += it
                open[entry.path] = it
            }
        } else {
            rows += mutableListOf(entry)
            open.remove(entry.path)
            entry.oldPath?.let { open.remove(it) }
        }
    }
    return rows.map { FeedRow(it) }
}
