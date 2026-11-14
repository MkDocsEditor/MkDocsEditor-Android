package de.markusressel.mkdocseditor.feature.history.domain

import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import kotlin.test.Test
import kotlin.test.assertEquals

class WordDiffTest {

    @Test
    fun `only the changed words of a line are marked`() {
        val changes = wordChanges("Install the app on your phone.", "Install the new app on the phone.")

        assertEquals(listOf(Highlight(19, 23)), changes.removed)
        assertEquals(listOf(Highlight(12, 16), Highlight(23, 26)), changes.added)
    }

    @Test
    fun `an emptied line is marked as a whole`() {
        assertEquals(WordChanges(listOf(Highlight(0, 3)), emptyList()), wordChanges("abc", ""))
    }

    @Test
    fun `removed lines pair with the added lines after them, in order`() {
        val lines = listOf(
            DiffLineModel(DiffOp.CONTEXT, "a"),
            DiffLineModel(DiffOp.REMOVED, "b"),
            DiffLineModel(DiffOp.REMOVED, "c"),
            DiffLineModel(DiffOp.ADDED, "B"),
            DiffLineModel(DiffOp.CONTEXT, "d"),
            DiffLineModel(DiffOp.ADDED, "e"),
        )

        assertEquals(listOf(null, 3, null, 1, null, null), pairChangedLines(lines))
    }
}
