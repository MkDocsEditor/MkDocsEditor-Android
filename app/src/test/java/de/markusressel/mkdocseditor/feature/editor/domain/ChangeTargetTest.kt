package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeTargetTest {

    /** a section from the line of its heading to the line behind it */
    private fun section(start: Int, end: Int) = ChangeTarget(start, listOf(LineRange(start, end)))

    private val page = """
        # Shopping

        ## Fruit
        - apples

        ## Drinks
        - water
        - juice
    """.trimIndent()

    @Test
    fun `a heading of the change is the section up to the next heading`() {
        assertEquals(section(5, 8), ChangeTarget.ofHeadings(page, listOf("Drinks")))
        assertEquals(section(2, 5), ChangeTarget.ofHeadings(page, listOf("Fruit")))
    }

    @Test
    fun `the first heading that the text still has wins`() {
        assertEquals(section(5, 8), ChangeTarget.ofHeadings(page, listOf("Vegetables", "Drinks", "Fruit")))
    }

    @Test
    fun `without a heading that the text has the page stays at the top`() {
        assertNull(ChangeTarget.ofHeadings(page, listOf("Vegetables")))
        assertNull(ChangeTarget.ofHeadings(page, emptyList()))
        assertNull(ChangeTarget.ofHeadings("no headings at all", listOf("Fruit")))
    }

    @Test
    fun `of several headings with the same name the first one counts`() {
        val text = "# A\n## Notes\none\n# B\n## Notes\ntwo"
        assertEquals(section(1, 3), ChangeTarget.ofHeadings(text, listOf("Notes")))
    }

    @Test
    fun `a heading in a code fence is no heading`() {
        val text = "# Setup\n```bash\n# Drinks\necho\n```\n## Drinks\nwater"
        assertEquals(section(5, 7), ChangeTarget.ofHeadings(text, listOf("Drinks")))
        assertEquals(section(0, 5), ChangeTarget.ofHeadings(text, listOf("Setup")))
    }

    @Test
    fun `a heading matches byte for byte as the backend names it, with its Markdown`() {
        val text = "# Intro\n## See [the guide](guide.md) {#guide}\ntext"
        assertEquals(section(1, 3), ChangeTarget.ofHeadings(text, listOf("See [the guide](guide.md) {#guide}")))
        assertNull(ChangeTarget.ofHeadings(text, listOf("See the guide")))
    }

    @Test
    fun `a heading in the front matter is no heading`() {
        val text = "---\n# Drinks\ntitle: x\n---\n# Drinks\nwater"
        assertEquals(section(4, 6), ChangeTarget.ofHeadings(text, listOf("Drinks")))
    }

    // the lines the backend sent, see "Changed lines" in docs/dashboard.md of the backend

    private val edited = "# Shopping\n\n## Fruit\n- apples\n- pears\n\n## Drinks\n- water\n"

    private fun lines(line: Int, count: Int, heading: String?, occurrence: Int, offset: Int) =
        ChangedLines(line, count, heading, occurrence, offset)

    private fun location(hash: String?, vararg ranges: ChangedLines) =
        ChangeLocation(headings = listOf("Fruit"), textHash = hash, ranges = ranges.toList())

    @Test
    fun `the lines apply as they are to the text of the edit`() {
        // "- pears" added under Fruit, line 5, 2 below its heading; the heading text is a lie the hash must win over
        val change = location(SummaryText.hash(edited), lines(5, 1, "Elsewhere", 1, 2), lines(8, 1, "Drinks", 1, 1))
        assertEquals(ChangeTarget(4, listOf(LineRange(4, 5), LineRange(7, 8)), exact = true), ChangeTarget.of(edited, change))
    }

    @Test
    fun `lines below a heading that moved go with the heading`() {
        val change = location(SummaryText.hash(edited), lines(5, 1, "Fruit", 1, 2))
        val moved = "# Shopping\n\nA new line.\nAnd one more.\n\n## Fruit\n- apples\n- pears\n"
        assertEquals(ChangeTarget(7, listOf(LineRange(7, 8)), exact = false), ChangeTarget.of(moved, change))
    }

    @Test
    fun `a repeated heading is found by its occurrence, or the first one if there are fewer`() {
        val text = "# A\n## Notes\none\n# B\n## Notes\ntwo\n"
        val second = location("0000000000000000", lines(6, 1, "Notes", 2, 1))
        assertEquals(ChangeTarget(5, listOf(LineRange(5, 6)), exact = false), ChangeTarget.of(text, second))
        val third = location("0000000000000000", lines(6, 1, "Notes", 3, 1))
        assertEquals(ChangeTarget(2, listOf(LineRange(2, 3)), exact = false), ChangeTarget.of(text, third))
    }

    @Test
    fun `without its heading a range keeps its line, clamped to the text`() {
        val text = "# Other\na\nb\n"
        val gone = location("0000000000000000", lines(2, 1, "Fruit", 1, 1), lines(40, 2, null, 0, 39))
        assertEquals(ChangeTarget(1, listOf(LineRange(1, 2), LineRange(2, 3)), exact = false), ChangeTarget.of(text, gone))
    }

    @Test
    fun `a removal is a place between lines, also at the end of the text`() {
        val text = "# A\nkept\n# B\n"
        val change = location(SummaryText.hash(text), lines(2, 0, "A", 1, 1), lines(4, 0, "B", 1, 1))
        assertEquals(ChangeTarget(1, listOf(LineRange(1, 1), LineRange(3, 3))), ChangeTarget.of(text, change))
    }

    @Test
    fun `an edit of only the newline at the end opens at the top`() {
        assertNull(ChangeTarget.of(edited, location(SummaryText.hash(edited))))
    }

    @Test
    fun `an entry from before the backend sent lines goes to the section of its heading`() {
        val old = ChangeLocation(headings = listOf("Drinks"), textHash = null, ranges = null)
        assertEquals(section(6, 8), ChangeTarget.of(edited, old))
    }

    @Test
    fun `lines and hash are read like the backend reads them`() {
        assertEquals(2, SummaryText.lines("a\nb\n").size)
        assertEquals(2, SummaryText.lines("a\nb").size)
        assertEquals(0, SummaryText.lines("").size)
        assertEquals(listOf("a\r", "b"), SummaryText.lines("a\r\nb"))
        // the first 16 hex digits of SHA-256 of "abc"
        assertEquals("ba7816bf8f01cfea", SummaryText.hash("abc"))
        // UTF-8 bytes and a kept \r, checked with sha256sum like the web client
        assertEquals("fadad47a2f205c1a", SummaryText.hash("Grüße\r\n"))
        assertEquals("e3b0c44298fc1c14", SummaryText.hash(""))
        assertEquals(4, SummaryText.frontMatterLines("---\na: 1\nb: 2\n---\ntext"))
        assertEquals(0, SummaryText.frontMatterLines("---\nnever closed"))
    }
}
