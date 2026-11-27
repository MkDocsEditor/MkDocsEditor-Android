package de.markusressel.mkdocseditor.feature.dashboard

import de.markusressel.mkdocseditor.feature.dashboard.domain.groupFeed
import de.markusressel.mkdocseditor.feature.dashboard.ui.DashboardViewModel.UiState
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind
import de.markusressel.mkdocsrestclient.dashboard.FeedSummaryModel
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeedGroupsTest {

    private var time = 1_000_000L

    /** each entry is older than the one before, like the feed */
    private fun entry(
        id: String,
        path: String,
        kind: String = FeedKind.EDITED,
        oldPath: String? = null,
        authors: List<FeedAuthorModel> = emptyList(),
        headings: List<String> = emptyList(),
    ) = FeedEntryModel(
        id = id,
        time = Date(time--),
        kind = kind,
        itemType = "document",
        path = path,
        oldPath = oldPath,
        authors = authors,
        summary = if (kind == FeedKind.EDITED) FeedSummaryModel(headings = headings) else null,
    )

    private fun ids(entries: List<FeedEntryModel>) = groupFeed(entries).map { row -> row.entries.map { it.id } }

    private fun author(name: String, accountId: String? = name) = FeedAuthorModel(accountId = accountId, displayName = name, color = "#000000")

    @Test
    fun `edits of a document fold into one row where the newest is, also with other documents in between`() {
        val feed = listOf(entry("1", "a.md"), entry("2", "b.md"), entry("3", "a.md"), entry("4", "c.md"), entry("5", "a.md"))

        assertEquals(listOf(listOf("1", "3", "5"), listOf("2"), listOf("4")), ids(feed))
    }

    @Test
    fun `only edits fold, and another kind of change of the document ends the group`() {
        val feed = listOf(
            entry("1", "a.md"),
            entry("2", "a.md", FeedKind.CREATED),
            entry("3", "a.md"),
            entry("4", "a.md", FeedKind.DELETED),
            entry("5", "a.md", FeedKind.DELETED),
            entry("6", "a.md"),
            entry("7", "a.md"),
        )

        assertEquals(listOf(listOf("1"), listOf("2"), listOf("3"), listOf("4"), listOf("5"), listOf("6", "7")), ids(feed))
    }

    @Test
    fun `a move ends the groups of the new and of the old path`() {
        val feed = listOf(
            entry("1", "new.md"),
            entry("2", "old.md"),
            entry("3", "new.md", FeedKind.MOVED, oldPath = "old.md"),
            entry("4", "new.md"),
            entry("5", "old.md"),
        )

        assertEquals(listOf(listOf("1"), listOf("2"), listOf("3"), listOf("4"), listOf("5")), ids(feed))
    }

    @Test
    fun `an older page only adds to the groups, and no row moves`() {
        val first = listOf(entry("1", "a.md"), entry("2", "b.md"), entry("3", "a.md"))
        val older = listOf(entry("4", "b.md"), entry("5", "a.md"), entry("6", "c.md"))

        assertEquals(listOf(listOf("1", "3"), listOf("2")), ids(first))
        assertEquals(listOf(listOf("1", "3", "5"), listOf("2", "4"), listOf("6")), ids(first + older))
    }

    @Test
    fun `a group has the authors in the order they first edited, and the headings of the newest edit first`() {
        val anna = author("Anna")
        val ben = author("Ben")
        val disk = author("External change", accountId = null)
        val row = groupFeed(
            listOf(
                entry("1", "a.md", authors = listOf(anna, disk), headings = listOf("Shopping", "Bakery")),
                entry("2", "a.md", authors = listOf(ben, anna), headings = listOf("Bakery", "Market")),
            ),
        ).single()

        assertEquals(listOf("Ben", "Anna", "External change"), row.authors.map { it.displayName })
        assertEquals(listOf("Shopping", "Bakery", "Market"), row.headings)
        assertEquals("1", row.newest.id)
    }

    @Test
    fun `an open group stays open when a newer edit or an older page joins it`() {
        val opened = UiState(feed = listOf(entry("2", "a.md"), entry("3", "a.md"))).let { state ->
            state.copy(expandedFeedEntries = state.feedRows.single().entries.map { it.id }.toSet())
        }
        val refreshed = opened.copy(feed = listOf(entry("1", "a.md")) + opened.feed + entry("4", "a.md"))

        assertTrue(refreshed.isExpanded(refreshed.feedRows.single()))
        assertFalse(refreshed.copy(expandedFeedEntries = emptySet()).isExpanded(refreshed.feedRows.single()))
    }
}
