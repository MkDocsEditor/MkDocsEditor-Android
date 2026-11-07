package de.markusressel.mkdocseditor.feature.filebrowser.data

import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.shownTitle
import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.editor.sectionData
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind
import de.markusressel.mkdocsrestclient.dashboard.PinModel
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageTitleTest {

    @Test
    fun `a page shows its title`() {
        val page = documentData("d1", name = "setup").copy(title = "Getting started")

        assertEquals("Getting started", page.shownTitle)
        assertEquals("setup", page.secondaryName)
    }

    @Test
    fun `a page from a backend without page-meta shows its file name`() {
        val page = documentData("d1", name = "setup")

        assertEquals("setup", page.shownTitle)
        assertNull(page.secondaryName)
        assertEquals("setup", documentData("d2", name = "setup").copy(title = " ").shownTitle)
    }

    @Test
    fun `a title made from the file name does not repeat it`() {
        assertNull(documentData("d1", name = "my-page").copy(title = "My page").secondaryName)
        assertNull(documentData("d1", name = "setup").copy(title = "Setup").secondaryName)
    }

    @Test
    fun `a folder lists its pages by title`() {
        val folder = sectionData(
            "s1",
            documents = listOf(
                documentData("a", name = "a-file").copy(title = "Zebra"),
                documentData("b", name = "b-file").copy(title = "Apple"),
                documentData("c", name = "mango"),
            ),
        )

        assertEquals(listOf("Apple", "mango", "Zebra"), folder.toFolderContent().documents.map { it.shownTitle })
    }

    @Test
    fun `pins and feed entries show the title, else the file name`() {
        val anna = FeedAuthorModel(displayName = "Anna", color = "#1971c2")
        val pin = PinModel("d1", "guide/setup.md", "setup", anna, Date(0))
        val entry = FeedEntryModel("e1", Date(0), FeedKind.EDITED, "document", "d1", "guide/setup.md")

        assertEquals("setup", pin.shownTitle)
        assertEquals("Getting started", pin.copy(title = "Getting started").shownTitle)
        assertEquals("setup", entry.shownTitle)
        assertEquals("Getting started", entry.copy(title = "Getting started").shownTitle)
    }
}
