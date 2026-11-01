package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextPositionsTest {

    private val text = "# Title\n\nSee [setup](guide/setup.md) and ![a](img.png)\n😀 [x](y.md)"

    @Test
    fun `lines and columns are 1-based`() {
        assertEquals(0, TextPositions.offsetOf(text, 1, 1))
        assertEquals(8, TextPositions.offsetOf(text, 2, 1))
        assertEquals(text.indexOf("guide/"), TextPositions.offsetOf(text, 3, 13))
    }

    @Test
    fun `columns count code points`() {
        // the emoji is one code point but two chars
        assertEquals(text.indexOf("y.md"), TextPositions.offsetOf(text, 4, 7))
    }

    @Test
    fun `positions are clamped to the text`() {
        assertEquals(text.length, TextPositions.offsetOf(text, 99, 1))
        assertEquals(7, TextPositions.offsetOf(text, 1, 99))
        assertEquals(0, TextPositions.offsetOf("", 1, 1))
    }

    @Test
    fun `the range covers the destination when it is still there`() {
        val start = text.indexOf("guide/")
        assertEquals(start until start + "guide/setup.md".length, TextPositions.rangeOf(text, 3, 13, "guide/setup.md"))
        assertTrue(TextPositions.rangeOf(text, 3, 13, "other.md").isEmpty())
    }
}
