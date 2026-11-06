package de.markusressel.mkdocseditor.feature.notifications.domain

import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.nameOf
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind
import java.util.Date

/**
 * What others changed on one page, for one notification
 *
 * @param authors display names of the other accounts, in the order of their first change
 * @param headings headings of the changed sections, without repeats
 * @param time the latest of the changes
 */
internal data class PageChange(
    val documentId: String,
    val pageName: String,
    val authors: List<String>,
    val headings: List<String>,
    val time: Date,
)

/**
 * Picks the changes worth a notification from new feed entries: edits of other accounts on the [watched] pages,
 * grouped per page. Own edits never count, and neither do edits without an account (`:disk`, `:server`).
 *
 * @param entries newest first, as the feed sends them
 * @param ownAccountId the account signed in on this device
 * @param watched the ids of the pinned pages and of the pages this account edited
 * @return one change per page, the most recently changed page first
 */
internal fun findPageChanges(
    entries: List<FeedEntryModel>,
    ownAccountId: String,
    watched: Set<String>,
): List<PageChange> = entries
    .filter { it.kind == FeedKind.EDITED && it.itemType == ITEM_DOCUMENT && it.documentId in watched }
    .mapNotNull { entry -> entry.withOthersOnly(ownAccountId) }
    .groupBy { it.documentId!! }
    .map { (documentId, edits) ->
        // the feed is newest first, but authors and headings read better in the order they happened
        val inOrder = edits.reversed()
        PageChange(
            documentId = documentId,
            pageName = nameOf(edits.first().path),
            authors = inOrder.flatMap { edit -> edit.authors.map { it.displayName } }.distinct(),
            headings = inOrder.flatMap { it.summary?.headings.orEmpty() }.distinct().take(MAX_HEADINGS),
            time = edits.first().time,
        )
    }

/** @return the entry with only the authors who are other accounts, null if there are none */
private fun FeedEntryModel.withOthersOnly(ownAccountId: String): FeedEntryModel? {
    val others = authors.filter { it.accountId != null && it.accountId != ownAccountId }
    return if (others.isEmpty()) null else copy(authors = others)
}

/**
 * The ids of the documents that [accountId] edited, from feed entries
 */
internal fun editedBy(entries: List<FeedEntryModel>, accountId: String): Set<String> = entries
    .filter { entry -> entry.kind == FeedKind.EDITED && entry.authors.any { it.accountId == accountId } }
    .mapNotNull { it.documentId }
    .toSet()

private const val ITEM_DOCUMENT = "document"
private const val MAX_HEADINGS = 5
