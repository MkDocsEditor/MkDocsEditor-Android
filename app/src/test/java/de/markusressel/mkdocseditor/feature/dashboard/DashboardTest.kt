package de.markusressel.mkdocseditor.feature.dashboard

import de.markusressel.mkdocseditor.feature.dashboard.domain.homePage
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.feedTime
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.folderOf
import de.markusressel.mkdocseditor.feature.dashboard.ui.compose.nameOf
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
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
}
