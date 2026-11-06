package de.markusressel.mkdocseditor.feature.editor.domain

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PreviewPositionsTest {

    // lines: 0 "# Title", 1 "", 2 "One", 3 "two", 4 "", 5 "- a", 6 "- b", 7 "", 8 "End"
    private val text = "# Title\n\nOne\ntwo\n\n- a\n- b\n\nEnd"

    private fun scroll(vararg blocks: PreviewBlock, height: Float = 100f, scrollY: Float = 50f, atTop: Boolean = false) =
        PreviewScroll(blocks.toList(), height, scrollY, atTop = atTop, atBottom = false, byUser = true)

    @Test
    fun `the lines go from the first block whose top is visible to the last block whose bottom is visible`() {
        val scroll = scroll(
            PreviewBlock(line = 0, top = -30f, bottom = -5f),
            PreviewBlock(line = 2, top = 0f, bottom = 40f),
            PreviewBlock(line = 5, top = 50f, bottom = 70f),
            PreviewBlock(line = 6, top = 80f, bottom = 120f),
            PreviewBlock(line = 8, top = 130f, bottom = 150f),
        )

        // the block at line 6 reaches out at the bottom, so the last whole one is at line 5 and ends before line 6
        assertEquals(2..5, visibleSourceLines(scroll))
    }

    @Test
    fun `a block ends where the next block with a later line starts, also when a nested one starts at its line`() {
        val scroll = scroll(
            PreviewBlock(line = 2, top = 10f, bottom = 40f),
            PreviewBlock(line = 5, top = 50f, bottom = 90f),
            // the paragraph in the list item starts at the same line
            PreviewBlock(line = 5, top = 50f, bottom = 60f),
            PreviewBlock(line = 8, top = 130f, bottom = 150f),
        )

        assertEquals(2..7, visibleSourceLines(scroll))
    }

    @Test
    fun `the last block of the page ends with the text`() {
        val scroll = scroll(PreviewBlock(line = 8, top = 10f, bottom = 30f))

        assertEquals(8..Int.MAX_VALUE, visibleSourceLines(scroll))
        assertEquals(text.indexOf("End") to text.length, viewportOfLines(text, 8..Int.MAX_VALUE))
    }

    @Test
    fun `a block taller than the view counts in full`() {
        val scroll = scroll(
            PreviewBlock(line = 2, top = -200f, bottom = 300f),
            PreviewBlock(line = 5, top = 310f, bottom = 330f),
        )

        assertEquals(2..4, visibleSourceLines(scroll))
    }

    @Test
    fun `the end of one block and the start of the next one, with neither one whole, make a range over both`() {
        val scroll = scroll(
            PreviewBlock(line = 2, top = -50f, bottom = 20f),
            PreviewBlock(line = 5, top = 30f, bottom = 200f),
            PreviewBlock(line = 8, top = 210f, bottom = 230f),
        )

        assertEquals(2..7, visibleSourceLines(scroll))
    }

    @Test
    fun `a page without room or without blocks shows no lines`() {
        assertNull(visibleSourceLines(scroll(PreviewBlock(line = 2, top = 0f, bottom = 10f), height = 0f)))
        assertNull(visibleSourceLines(scroll()))
    }

    @Test
    fun `the viewport goes from the start of the first line to the end of the last line`() {
        // lines 2 to 4: "One", "two" and the empty line before the list, which ends right before the list
        assertEquals(text.indexOf("One") to text.indexOf("- a") - 1, viewportOfLines(text, 2..4))
        assertEquals(text.indexOf("- a") to text.indexOf("- b") + 3, viewportOfLines(text, 5..6))
    }

    @Test
    fun `the viewport of an empty line is where it is`() {
        assertEquals(8 to 8, viewportOfLines(text, 1..1))
        assertEquals(0 to 0, viewportOfLines("", 0..Int.MAX_VALUE))
    }

    @Test
    fun `lines behind the end of the text are its last line`() {
        assertEquals(text.indexOf("End") to text.length, viewportOfLines(text, 20..30))
    }

    @Test
    fun `the editor sends its first and last visible character, the page its lines, by what is shown`() {
        val range = TextRange(9, 17)

        assertEquals(9 to 16, localViewport(editorShown = true, editorRange = range, previewLines = 5..6, text = text))
        assertEquals(
            text.indexOf("- a") to text.indexOf("- b") + 3,
            localViewport(editorShown = false, editorRange = range, previewLines = 5..6, text = text),
        )
        assertNull(localViewport(editorShown = true, editorRange = TextRange(4), previewLines = 5..6, text = text))
        assertNull(localViewport(editorShown = false, editorRange = range, previewLines = null, text = text))
    }

    @Test
    fun `the top line lies between the tops of the blocks around the top of the view`() {
        val scroll = scroll(
            PreviewBlock(line = 2, top = -20f, bottom = 30f),
            PreviewBlock(line = 2, top = -20f, bottom = 0f),
            PreviewBlock(line = 6, top = 20f, bottom = 60f),
        )

        // half way from the block at line 2 to the one at line 6
        assertEquals(4.0, topSourceLine(scroll, lineCountOf(text)))
    }

    @Test
    fun `at the top of the page the top line is the first one`() {
        val scroll = scroll(PreviewBlock(line = 2, top = 12f, bottom = 30f), scrollY = 0f, atTop = true)

        assertEquals(0.0, topSourceLine(scroll, lineCountOf(text)))
    }

    @Test
    fun `inside the last block the top line goes on to the end of the text`() {
        val scroll = scroll(PreviewBlock(line = 5, top = -50f, bottom = 50f))

        // half of the block, which goes from line 5 to the end at line 9
        assertEquals(7.0, topSourceLine(scroll, lineCountOf(text)))
    }

    @Test
    fun `a line with a fraction is a part of the way through it, and back`() {
        val start = text.indexOf("One")

        assertEquals(start, positionOfLine(text, 2.0))
        // "One\n" has four characters
        assertEquals(start + 2, positionOfLine(text, 2.5))
        assertEquals(2.5, lineOfPosition(text, start + 2))
        assertEquals(0, positionOfLine(text, -1.0))
        assertEquals(text.length, positionOfLine(text, 99.0))
    }
}
