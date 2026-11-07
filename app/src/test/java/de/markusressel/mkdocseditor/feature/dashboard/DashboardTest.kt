package de.markusressel.mkdocseditor.feature.dashboard

import de.markusressel.mkdocseditor.feature.dashboard.domain.homePage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import de.markusressel.mkdocseditor.feature.dashboard.ui.changedHeadings
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.changeCounts
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.feedTime
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.folderOf
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.nameOf
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind
import de.markusressel.mkdocsrestclient.dashboard.FeedSummaryModel
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardTest {

    private fun document(id: String, name: String) = DocumentData(0, id, name, 0, Date(0), "", content = null, isOfflineAvailable = false)

    @Test
    fun `the home page is the index in the top folder only`() {
        val nested = section("s", "guide", documents = listOf(document("d2", "index")))
        assertNull(WikiTree(section("root", "root", subsections = listOf(nested))).homePage())

        val tree = WikiTree(section("root", "root", subsections = listOf(nested), documents = listOf(document("d1", "index"))))
        assertEquals("d1", tree.homePage()?.id)
    }

    @Test
    fun `a feed path shows as the page name and its folder`() {
        assertEquals("setup", nameOf("guide/setup.md"))
        assertEquals("guide/sub", folderOf("guide/sub/setup.md"))
        assertEquals("index", nameOf("index.md"))
        assertNull(folderOf("index.md"))
    }

    @Test
    fun `a change from a server whose clock is ahead counts as just now`() {
        val now = Date(1_000_000)
        assertNull(feedTime(Date(1_030_000), now))
        assertNull(feedTime(Date(970_000), now))
    }

    @Test
    fun `the counts of an edit are green and red`() {
        val green = Color(0xFF1A7A2D)
        val red = Color(0xFFB3261E)
        val counts = changeCounts(added = "+3", removed = "−1", addedColor = green, removedColor = red)

        assertEquals("+3 −1", counts.text)
        val styles = counts.spanStyles.map { it.item to (it.start until it.end) }
        assertEquals(listOf(SpanStyle(color = green) to (0 until 2), SpanStyle(color = red) to (3 until 5)), styles)
    }

    private fun entry(kind: String) = FeedEntryModel(
        id = "1", time = Date(0), kind = kind, itemType = "document", documentId = "d1", path = "shopping.md",
        summary = FeedSummaryModel(linesAdded = 3, linesRemoved = 1, headings = listOf("Fruit")),
    )

    @Test
    fun `only an edit opens the page at the section it changed`() {
        assertEquals(listOf("Fruit"), entry(FeedKind.EDITED).changedHeadings())
        assertEquals(emptyList(), entry(FeedKind.CREATED).changedHeadings())
        assertEquals(emptyList(), entry(FeedKind.MOVED).changedHeadings())
        assertEquals(emptyList(), entry(FeedKind.DELETED).changedHeadings())
    }
}
