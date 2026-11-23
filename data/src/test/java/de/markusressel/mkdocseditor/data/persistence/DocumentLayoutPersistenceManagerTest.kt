package de.markusressel.mkdocseditor.data.persistence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DocumentLayoutPersistenceManagerTest : PersistenceTestBase() {

    private fun view(top: Double) = SavedDocumentView(
        editorTopLine = top,
        pageTopLine = top + 1,
        editorZoom = 1.5f,
        selectionStartLine = 2,
        selectionStartColumn = 3,
        selectionEndLine = 4,
        selectionEndColumn = 5,
        anchorLine = 6,
        anchorText = "## Usage",
    )

    @Test
    fun `the view of a document is saved, next to its split ratio`() {
        layouts.saveSplitRatio("a", 0.3f)
        layouts.saveView("a", view(10.5))

        assertEquals(view(10.5), layouts.getView("a"))
        assertEquals(0.3f, layouts.getSplitRatio("a"))
        assertNull(layouts.getView("b"))
        // a row with only a view has no split ratio
        layouts.saveView("b", view(1.0))
        assertNull(layouts.getSplitRatio("b"))
    }

    @Test
    fun `only the newest views are kept, and a split ratio stays`() {
        layouts.saveSplitRatio("doc-0", 0.4f)
        repeat(DocumentLayoutPersistenceManager.MAX_VIEWS + 2) { layouts.saveView("doc-$it", view(it.toDouble()), now = 1_000L + it) }

        assertNull(layouts.getView("doc-0"))
        assertNull(layouts.getView("doc-1"))
        assertNotNull(layouts.getView("doc-2"))
        assertEquals(0.4f, layouts.getSplitRatio("doc-0"))
        assertEquals(DocumentLayoutPersistenceManager.MAX_VIEWS + 1L, layouts.standardOperation().count())
    }

    @Test
    fun `documents that are not in the wiki anymore are forgotten`() {
        layouts.saveView("kept", view(1.0))
        layouts.saveView("deleted", view(2.0))

        sections.insertOrUpdateRoot(section("root", documents = listOf("kept")))

        assertNotNull(layouts.getView("kept"))
        assertNull(layouts.getView("deleted"))
    }
}
