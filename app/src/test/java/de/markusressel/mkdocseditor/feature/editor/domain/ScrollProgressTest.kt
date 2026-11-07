package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScrollProgressTest {

    /** blocks at lines 2, 5 and 8 of a text with ten lines, on a page of 1000 pixels */
    private val map = SourceHeightMap(
        PreviewLayout(
            blocks = listOf(
                PreviewLayoutBlock(line = 2, top = 100f),
                PreviewLayoutBlock(line = 5, top = 400f),
                PreviewLayoutBlock(line = 8, top = 700f),
            ),
            scrollHeight = 1000f,
        ),
        lineCount = 10,
    )

    private fun scroll(scrollY: Float, height: Float = 200f, scrollHeight: Float = 1000f) =
        PreviewScroll(emptyList(), height, scrollY, scrollHeight, byUser = true)

    /** an editor that shows [visibleLines] lines from [top], and can scroll by 100 pixels for each line it hides */
    private class FakeEditor(var top: Double, val visibleLines: Double = 5.0, val lineCount: Int = 10) : EditorScrollView {
        override val scrollOffset get() = (top * 100).toFloat()
        override val maxScrollOffset get() = ((lineCount - visibleLines) * 100).toFloat()
        override fun lineAt(viewportFraction: Float) = top + viewportFraction * visibleLines
    }

    private fun assertAnchor(expected: SyncAnchor, actual: SyncAnchor?) {
        assertEquals(expected.line, assertNotNull(actual).line, 0.0001)
        assertEquals(expected.viewportFraction, actual.viewportFraction, 0.0001f)
    }

    @Test
    fun `the map runs from the start of the page through the tops of the blocks to the end of the page`() {
        assertEquals(0.0, map.lineAt(0f))
        // half way from the start of the page to the block at line 2
        assertEquals(1.0, map.lineAt(50f))
        assertEquals(2.0, map.lineAt(100f))
        assertEquals(3.5, map.lineAt(250f))
        // the last block runs to the end of the text, at the end of the page
        assertEquals(9.0, map.lineAt(850f))
        assertEquals(10.0, map.lineAt(1000f))
    }

    @Test
    fun `the front matter runs from the start of the page to the first block, past the title made from it`() {
        // lines 0 to 4 are the front matter: the renderer puts a title at line 0 and the tags below it
        val page = SourceHeightMap(
            PreviewLayout(
                blocks = listOf(
                    PreviewLayoutBlock(line = 0, top = 0f),
                    PreviewLayoutBlock(line = 5, top = 200f),
                    PreviewLayoutBlock(line = 8, top = 500f),
                ),
                scrollHeight = 1000f,
            ),
            lineCount = 10,
        )

        assertEquals(0.0, page.lineAt(0f))
        assertEquals(2.5, page.lineAt(100f))
        assertEquals(5.0, page.lineAt(200f))
        assertEquals(6.5, page.lineAt(350f))
    }

    @Test
    fun `heights outside the page are its first and its last line`() {
        assertEquals(0.0, map.lineAt(-50f))
        assertEquals(10.0, map.lineAt(5000f))
    }

    @Test
    fun `a nested block that starts at the line of the one around it, or above it, adds nothing`() {
        val nested = SourceHeightMap(
            PreviewLayout(
                blocks = listOf(
                    PreviewLayoutBlock(line = 0, top = 20f),
                    PreviewLayoutBlock(line = 2, top = 100f),
                    // the paragraph in a list item, at the line of the item and a little lower
                    PreviewLayoutBlock(line = 2, top = 110f),
                    PreviewLayoutBlock(line = 1, top = 150f),
                    PreviewLayoutBlock(line = 5, top = 400f),
                    // behind the end of the text, from a page that is not up to date
                    PreviewLayoutBlock(line = 12, top = 900f),
                ),
                scrollHeight = 1000f,
            ),
            lineCount = 10,
        )

        // the block at line 0 is the start of the page
        assertEquals(1.0, nested.lineAt(50f))
        assertEquals(3.0, nested.lineAt(200f))
        assertEquals(7.5, nested.lineAt(700f))
    }

    @Test
    fun `a page without blocks runs straight from the first line to the end of the text`() {
        val empty = SourceHeightMap(PreviewLayout(emptyList(), scrollHeight = 500f), lineCount = 10)

        assertEquals(5.0, empty.lineAt(250f))
    }

    @Test
    fun `the progress is 0 when a view can't scroll`() {
        assertEquals(0f, scrollProgress(0f, 0f))
        assertEquals(0.5f, scrollProgress(50f, 100f))
        assertEquals(1f, scrollProgress(120f, 100f))
    }

    @Test
    fun `the reading line of the page is as far down its view as the page is scrolled`() {
        // scrolled 400 of 800: the reading line is at 400 + 100, half way from line 5 to line 8
        assertEquals(SyncAnchor(line = 6.0, viewportFraction = 0.5f), previewReadingAnchor(map, scroll(scrollY = 400f)))
    }

    @Test
    fun `the start and the end of the page are the start and the end of the text`() {
        assertEquals(SyncAnchor(0.0, 0f), previewReadingAnchor(map, scroll(scrollY = 0f)))
        assertEquals(SyncAnchor(10.0, 1f), previewReadingAnchor(map, scroll(scrollY = 800f)))
        // a page that fits its view does not scroll, and stays at the start
        assertEquals(SyncAnchor(0.0, 0f), previewReadingAnchor(map, scroll(scrollY = 0f, height = 2000f)))
    }

    @Test
    fun `the reading line of the editor is as far down its view as the editor is scrolled`() {
        // scrolled 2 of 5 lines: 40 %, two lines into the five that it shows
        assertAnchor(SyncAnchor(4.0, 0.4f), editorReadingAnchor(FakeEditor(top = 2.0)))
        assertEquals(SyncAnchor(0.0, 0f), editorReadingAnchor(FakeEditor(top = 0.0)))
        assertEquals(SyncAnchor(10.0, 1f), editorReadingAnchor(FakeEditor(top = 5.0)))
        assertNull(editorReadingAnchor(object : EditorScrollView {
            override val scrollOffset: Float? = null
            override val maxScrollOffset: Float? = null
            override fun lineAt(viewportFraction: Float): Double? = null
        }))
    }

    @Test
    fun `the editor puts the reading line of the page at the same height, the end of the text at its bottom`() {
        assertEquals(EditorTarget(line = 6, fraction = 0.25f, viewportFraction = 0.5f), editorTargetOf(SyncAnchor(6.25, 0.5f), 10))
        assertEquals(EditorTarget(0, 0f, 0f), editorTargetOf(SyncAnchor(0.0, 0f), 10))
        assertEquals(EditorTarget(9, 1f, 1f), editorTargetOf(SyncAnchor(10.0, 1f), 10))
        assertEquals(EditorTarget(9, 1f, 1f), editorTargetOf(SyncAnchor(12.0, 1f), 10))
    }

    @Test
    fun `the caret is at its height in the editor`() {
        val anchor = assertNotNull(editorCaretAnchor(FakeEditor(top = 2.0), caretLine = 3.5))

        assertEquals(3.5, anchor.line)
        // one and a half of the five lines it shows
        assertEquals(0.3f, anchor.viewportFraction, 0.001f)
    }

    @Test
    fun `a caret out of view has no anchor`() {
        assertNull(editorCaretAnchor(FakeEditor(top = 2.0), caretLine = 1.0))
        assertNull(editorCaretAnchor(FakeEditor(top = 2.0), caretLine = 7.5))
    }
}
