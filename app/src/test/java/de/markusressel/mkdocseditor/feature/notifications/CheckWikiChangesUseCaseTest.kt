package de.markusressel.mkdocseditor.feature.notifications

import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.feature.notifications.data.WikiChangesMarks
import de.markusressel.mkdocseditor.feature.notifications.domain.CheckWikiChangesUseCase
import de.markusressel.mkdocseditor.feature.notifications.domain.PageChange
import de.markusressel.mkdocseditor.feature.notifications.domain.SignedInAccount
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesNotifier
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesServer
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind
import de.markusressel.mkdocsrestclient.dashboard.FeedPageModel
import de.markusressel.mkdocsrestclient.dashboard.FeedSummaryModel
import de.markusressel.mkdocsrestclient.dashboard.PinModel
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckWikiChangesUseCaseTest {

    private class MapStore : KeyValueStore {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) {
            values[key] = value
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    /** a feed in pages of [pageSize], newest first */
    private class FakeServer : WikiChangesServer {
        var account: SignedInAccount? = SignedInAccount(backendId = 1, accountId = ME)
        var pins = PinsModel()
        var feed = listOf<FeedEntryModel>()
        var pageSize = 100
        var failing = false
        val requestedPages = mutableListOf<String?>()

        override suspend fun signedInAccount() = account

        override suspend fun pins(): PinsModel = pins

        override suspend fun feed(before: String?, limit: Int): FeedPageModel {
            if (failing) throw IOException("offline")
            requestedPages += before
            val start = before?.toInt() ?: 0
            val end = minOf(start + minOf(limit, pageSize), feed.size)
            return FeedPageModel(entries = feed.subList(start, end), next = if (end < feed.size) "$end" else null)
        }
    }

    private val server = FakeServer()
    private val marks = WikiChangesMarks(MapStore())
    private val shown = mutableListOf<PageChange>()
    private val check = CheckWikiChangesUseCase(
        server = server,
        marks = marks,
        notifier = object : WikiChangesNotifier {
            override fun show(backendId: Long, changes: List<PageChange>) {
                shown += changes
            }
        },
    )

    private var clock = 1_000L

    /** adds a newer entry on top of the feed */
    private fun edit(
        documentId: String,
        vararg authors: FeedAuthorModel,
        headings: List<String> = emptyList(),
        kind: String = FeedKind.EDITED,
    ): FeedEntryModel {
        clock += 1_000
        val entry = FeedEntryModel(
            id = "$clock",
            time = Date(clock),
            kind = kind,
            itemType = "document",
            documentId = documentId,
            path = "family/$documentId.md",
            authors = authors.toList(),
            summary = FeedSummaryModel(linesAdded = 1, headings = headings),
        )
        server.feed = listOf(entry) + server.feed
        return entry
    }

    private fun pin(documentId: String) = PinModel(documentId, "family/$documentId.md", documentId, ANNA, Date(0))

    /** the first check only sets the mark */
    private suspend fun started() {
        check()
        shown.clear()
    }

    @Test
    fun `the first check notifies nothing and only remembers where the feed is`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        edit("shopping", ANNA)

        check()

        assertTrue(shown.isEmpty())
        assertEquals(clock, marks.seenUntil(1, ME))
    }

    @Test
    fun `the first check on an empty feed still counts as the first`() = runTest {
        check()
        server.pins = PinsModel(family = listOf(pin("shopping")))
        edit("shopping", ANNA)

        check()

        assertEquals(listOf("shopping"), shown.map { it.documentId })
    }

    @Test
    fun `changes of others to pages pinned for everyone and for me notify`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")), personal = listOf(pin("recipes")))
        started()
        edit("shopping", ANNA)
        edit("recipes", BEN)

        check()

        assertEquals(setOf("shopping", "recipes"), shown.map { it.documentId }.toSet())
    }

    @Test
    fun `changes to pages I edited notify, others do not`() = runTest {
        edit("garden", ME_AUTHOR)
        started()
        edit("garden", ANNA)
        edit("car", ANNA)

        check()

        assertEquals(listOf("garden"), shown.map { it.documentId })
    }

    @Test
    fun `a page I edit after the first check is followed too`() = runTest {
        started()
        edit("garden", ME_AUTHOR)
        check()
        edit("garden", ANNA)

        check()

        assertEquals(listOf("garden"), shown.map { it.documentId })
    }

    @Test
    fun `my own changes never notify`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        edit("shopping", ME_AUTHOR)

        check()

        assertTrue(shown.isEmpty())
    }

    @Test
    fun `a change made together names only the others`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        edit("shopping", ME_AUTHOR, ANNA)

        check()

        assertEquals(listOf("Anna"), shown.single().authors)
    }

    @Test
    fun `changes without an account do not notify`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        edit("shopping", FeedAuthorModel(displayName = "External change", color = "#888888"))

        check()

        assertTrue(shown.isEmpty())
    }

    @Test
    fun `moves and deletes of a pinned page do not notify`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        edit("shopping", ANNA, kind = FeedKind.MOVED)

        check()

        assertTrue(shown.isEmpty())
    }

    @Test
    fun `the same change never notifies twice`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        edit("shopping", ANNA)

        check()
        check()

        assertEquals(1, shown.size)
    }

    @Test
    fun `an editing session that goes on notifies again`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        val first = edit("shopping", ANNA)
        check()
        // the backend keeps the entry and moves it up the feed
        clock += 1_000
        server.feed = listOf(first.copy(time = Date(clock), authors = listOf(ANNA, BEN))) + server.feed.drop(1)

        check()

        assertEquals(listOf(listOf("Anna"), listOf("Anna", "Ben")), shown.map { it.authors })
    }

    @Test
    fun `several changes of one page are one notification with all authors and headings`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        edit("shopping", ANNA, headings = listOf("Fruit"))
        edit("shopping", BEN, headings = listOf("Fruit", "Drinks"))
        edit("shopping", ANNA)

        check()

        val change = shown.single()
        assertEquals("shopping", change.pageName)
        assertEquals(listOf("Anna", "Ben"), change.authors)
        assertEquals(listOf("Fruit", "Drinks"), change.headings)
        assertEquals(Date(clock), change.time)
    }

    @Test
    fun `the feed is read back to the mark over several pages`() = runTest {
        server.pageSize = 2
        server.pins = PinsModel(family = listOf(pin("a"), pin("b"), pin("c"), pin("old")))
        edit("old", ANNA)
        edit("old", BEN)
        started()
        edit("a", ANNA)
        edit("b", ANNA)
        edit("c", ANNA)
        server.requestedPages.clear()

        check()

        assertEquals(setOf("a", "b", "c"), shown.map { it.documentId }.toSet())
        assertEquals(listOf(null, "2"), server.requestedPages)
    }

    @Test
    fun `without a session nothing is asked`() = runTest {
        server.account = null
        server.failing = true

        assertTrue(check())
    }

    @Test
    fun `a failed request is tried again and keeps the mark`() = runTest {
        started()
        val mark = marks.seenUntil(1, ME)
        server.failing = true

        assertFalse(check())
        assertEquals(mark, marks.seenUntil(1, ME))
    }

    @Test
    fun `the mark is kept per account`() = runTest {
        server.pins = PinsModel(family = listOf(pin("shopping")))
        started()
        server.account = SignedInAccount(backendId = 1, accountId = "someone-else")
        edit("shopping", ANNA)

        check()

        assertTrue(shown.isEmpty())
    }

    private companion object {
        const val ME = "me"
        val ME_AUTHOR = FeedAuthorModel(accountId = ME, username = "me", displayName = "Me", color = "#000000")
        val ANNA = FeedAuthorModel(accountId = "a1", username = "anna", displayName = "Anna", color = "#1971c2")
        val BEN = FeedAuthorModel(accountId = "b1", username = "ben", displayName = "Ben", color = "#2f9e44")
    }
}
