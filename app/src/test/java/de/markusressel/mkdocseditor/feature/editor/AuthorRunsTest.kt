package de.markusressel.mkdocseditor.feature.editor

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRun
import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.LineShare
import de.markusressel.mkdocseditor.feature.editor.domain.lineShares
import de.markusressel.mkdocseditor.feature.editor.domain.toAuthorRuns
import de.markusressel.mkdocseditor.feature.editor.ui.compose.AuthorHighlightMode
import de.markusressel.mkdocseditor.feature.editor.ui.compose.authorColor
import de.markusressel.mkdocseditor.feature.editor.ui.compose.fallbackPeerColors
import de.markusressel.mkdocsrestclient.reference.AuthorshipModel
import de.markusressel.mkdocsrestclient.reference.AuthorshipRunModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AuthorRunsTest {

    private val ab = AuthorRuns(listOf(AuthorRun(0, 5, "a"), AuthorRun(5, 10, "b")))

    private fun model(vararg runs: AuthorshipRunModel) = AuthorshipModel(emptyList(), runs.toList())

    @Test
    fun `an insert inside a run splits it and has no author yet`() {
        assertEquals(
            listOf(AuthorRun(0, 2, "a"), AuthorRun(2, 5, null), AuthorRun(5, 8, "a"), AuthorRun(8, 13, "b")),
            ab.applied(2, 0, 3).runs,
        )
    }

    @Test
    fun `a delete across runs shortens both`() {
        assertEquals(listOf(AuthorRun(0, 3, "a"), AuthorRun(3, 6, "b")), ab.applied(3, 4, 0).runs)
    }

    @Test
    fun `deleting a whole run removes it`() {
        assertEquals(listOf(AuthorRun(0, 5, "b")), ab.applied(0, 5, 0).runs)
    }

    @Test
    fun `a replacement puts the new text where the old one was`() {
        assertEquals(
            listOf(AuthorRun(0, 3, "a"), AuthorRun(3, 4, null), AuthorRun(4, 7, "b")),
            ab.applied(3, 4, 1).runs,
        )
    }

    @Test
    fun `text added at the end has no author yet`() {
        assertEquals(AuthorRun(10, 12, null), ab.applied(10, 0, 2).runs.last())
    }

    @Test
    fun `text added at the very start comes first`() {
        assertEquals(AuthorRun(0, 2, null), ab.applied(0, 0, 2).runs.first())
    }

    @Test
    fun `an insert with an author gets a run of that author`() {
        assertEquals(
            listOf(AuthorRun(0, 2, "a"), AuthorRun(2, 4, "c"), AuthorRun(4, 7, "a"), AuthorRun(7, 12, "b")),
            ab.applied(2, 0, 2, author = "c").runs,
        )
    }

    @Test
    fun `an insert next to a run of the same author merges with it`() {
        assertEquals(listOf(AuthorRun(0, 7, "a"), AuthorRun(7, 12, "b")), ab.applied(5, 0, 2, author = "a").runs)
        assertEquals(listOf(AuthorRun(0, 5, "a"), AuthorRun(5, 12, "b")), ab.applied(10, 0, 2, author = "b").runs)
    }

    @Test
    fun `an insert inside the run of another author splits it`() {
        assertEquals(
            listOf(AuthorRun(0, 5, "a"), AuthorRun(5, 7, "b"), AuthorRun(7, 9, "a"), AuthorRun(9, 12, "b")),
            ab.applied(7, 0, 2, author = "a").runs,
        )
    }

    @Test
    fun `a delete is the same with an author`() {
        assertEquals(ab.applied(3, 4, 0), ab.applied(3, 4, 0, author = "c"))
        assertEquals(ab.applied(0, 5, 0), ab.applied(0, 5, 0, author = "c"))
    }

    @Test
    fun `a replacement with an author puts its run where the old text was`() {
        assertEquals(
            listOf(AuthorRun(0, 3, "a"), AuthorRun(3, 4, "c"), AuthorRun(4, 7, "b")),
            ab.applied(3, 4, 1, author = "c").runs,
        )
    }

    @Test
    fun `an insert with an author into text without runs gets a run of its own`() {
        assertEquals(listOf(AuthorRun(3, 5, "c")), AuthorRuns().applied(3, 0, 2, author = "c").runs)
        assertEquals(AuthorRuns(), AuthorRuns().applied(3, 2, 0, author = "c"))
    }

    @Test
    fun `no runs stay no runs, so an edit does not look like all the text`() {
        assertEquals(AuthorRuns(), AuthorRuns().applied(0, 0, 3))
    }

    @Test
    fun `code points of the backend become utf-16 indices`() {
        val runs = model(AuthorshipRunModel(0, 2, "1", "a"), AuthorshipRunModel(2, 3, "2", "b")).toAuthorRuns("😀x😀")

        assertEquals(listOf(AuthorRun(0, 3, "a"), AuthorRun(3, 5, "b")), runs?.runs)
    }

    @Test
    fun `imported text has no author`() {
        val runs = model(AuthorshipRunModel(0, 1, "108d", ":disk"), AuthorshipRunModel(1, 2, "108d", ":server")).toAuthorRuns("ab")

        assertEquals(listOf(AuthorRun(0, 2, null)), runs?.runs)
    }

    @Test
    fun `runs behind the end of the text do not fit`() {
        assertNull(model(AuthorshipRunModel(0, 5, "1", "a")).toAuthorRuns("ab"))
    }

    @Test
    fun `a line shows the share of each author`() {
        val shares = lineShares("abcd\nef\n", listOf(AuthorRun(0, 1, "a"), AuthorRun(1, 4, "b"), AuthorRun(4, 7, "c")))

        assertEquals(
            listOf(listOf(LineShare("a", 0.25f), LineShare("b", 0.75f)), listOf(LineShare("c", 1f)), emptyList()),
            shares,
        )
    }

    @Test
    fun `the button in the editor goes through all modes`() {
        val start = AuthorHighlightMode.StripAndMarker

        assertEquals(AuthorHighlightMode.Strip, start.next())
        assertEquals(AuthorHighlightMode.Off, start.next().next())
        assertEquals(start, start.next().next().next())
    }

    @Test
    fun `an author has exactly the color of the server`() {
        assertEquals(Color(0xFFE8590C), authorColor("markus", mapOf("markus" to "#e8590c")))
    }

    @Test
    fun `a name with the smallest possible hash still gets a color`() {
        assertEquals(Int.MIN_VALUE, "polygenelubricants".hashCode())
        assertEquals(fallbackPeerColors.first(), authorColor("polygenelubricants", emptyMap()))
    }
}
