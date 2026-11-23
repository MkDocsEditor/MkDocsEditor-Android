package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SavedViewTest {

    private val text = "# Title\n\nintro\n\n## Usage\nrun it\n\n## End\nbye"

    @Test
    fun `an unchanged text gets the same view back`() {
        val saved = captureView(text, editorTop = 4.5, pageTop = 4.0, editorZoom = 1.5f, selectionStart = 30, selectionEnd = 33)

        assertEquals(PlacedView(4.5, 4.0, 1.5f, 30, 33), saved.placeIn(text))
    }

    @Test
    fun `lines that others added above move the view along`() {
        val saved = captureView(text, editorTop = 4.0, pageTop = 3.5, editorZoom = null, selectionStart = text.indexOf("run"), selectionEnd = text.indexOf("run"))
        val changed = "# Title\n\nnew\nlines\nhere\n\nintro\n\n## Usage\nrun it\n\n## End\nbye"

        val placed = saved.placeIn(changed)
        assertEquals(8.0, placed.editorTop)
        assertEquals(7.5, placed.pageTop)
        assertEquals(changed.indexOf("run"), placed.selectionStart)
    }

    @Test
    fun `an empty line at the top anchors on the next line with text`() {
        val saved = captureView(text, editorTop = 3.0, pageTop = null, editorZoom = null, selectionStart = 0, selectionEnd = 0)

        assertEquals(4, saved.anchorLine)
        assertEquals("## Usage", saved.anchorText)
        assertEquals(4.0, saved.placeIn("added\n$text").editorTop)
    }

    @Test
    fun `of several copies of the anchor line, the nearest one counts`() {
        val lines = "x\n1\n2\n3\nx\n5\n6"
        val saved = captureView(lines, editorTop = 4.0, pageTop = null, editorZoom = null, selectionStart = 0, selectionEnd = 0)

        // the first copy is now 3 lines away, the second one 1
        assertEquals(5.0, saved.placeIn("new\n$lines").editorTop)
    }

    @Test
    fun `without the anchor line the view stays at its lines, clamped to a shorter text`() {
        val saved = captureView(text, editorTop = 7.0, pageTop = 8.0, editorZoom = null, selectionStart = text.length, selectionEnd = text.length)

        val placed = saved.placeIn("short\ntext")
        assertEquals(2.0, placed.editorTop)
        assertEquals(2.0, placed.pageTop)
        assertEquals("short\ntext".length, placed.selectionStart)
    }

    @Test
    fun `a cursor never ends up inside an emoji`() {
        val saved = captureView("ab\ncd", editorTop = null, pageTop = null, editorZoom = null, selectionStart = 4, selectionEnd = 4)

        assertEquals(3, saved.placeIn("ab\n😀").selectionStart)
    }

    @Test
    fun `a view of the page only has no line of the editor`() {
        assertNull(captureView(text, editorTop = null, pageTop = 2.0, editorZoom = null, selectionStart = 0, selectionEnd = 0).placeIn(text).editorTop)
    }
}
