package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.text.TextRange
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewBlock
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewScroll
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScrollSyncTest {

    // ten lines of ten characters each, with the line break
    private val text = (0 until 10).joinToString("\n") { "line $it   " }
    private val sync = ScrollSync { text }.apply { active = true }

    /** the page, scrolled so that its top is half way from line 4 to line 6 */
    private fun previewScroll(byUser: Boolean, atBottom: Boolean = false) = PreviewScroll(
        blocks = listOf(PreviewBlock(line = 4, top = -10f, bottom = 5f), PreviewBlock(line = 6, top = 10f, bottom = 30f)),
        height = 100f,
        scrollY = 200f,
        atTop = false,
        atBottom = atBottom,
        byUser = byUser,
    )

    @Test
    fun `the editor drives the page at first`() {
        sync.onEditorVisibleRange(TextRange(25, 60))

        assertEquals(2.5, sync.previewLine)
        assertNull(sync.editorTarget)
    }

    @Test
    fun `scrolling the page by hand moves the editor, so that its top line is the top line of the page`() {
        sync.onEditorVisibleRange(TextRange(0, 30))
        sync.onPreviewScrolled(previewScroll(byUser = true))

        assertEquals(ScrollSync.Side.Preview, sync.driver)
        // half way from line 4 to line 6 is the start of line 5, and the editor centers as much text as it shows now
        assertEquals(50..80, sync.editorTarget)
    }

    @Test
    fun `while the page drives, the editor moving along does not move the page back`() {
        sync.onEditorVisibleRange(TextRange(0, 30))
        sync.onPreviewScrolled(previewScroll(byUser = true))
        val before = sync.previewLine

        sync.onEditorVisibleRange(TextRange(50, 80))

        assertEquals(before, sync.previewLine)
    }

    @Test
    fun `a scroll of the page that the app made does not drive the editor`() {
        sync.onEditorVisibleRange(TextRange(25, 60))
        sync.onPreviewScrolled(previewScroll(byUser = false))

        assertEquals(ScrollSync.Side.Editor, sync.driver)
        assertNull(sync.editorTarget)
    }

    @Test
    fun `moving the editor by hand takes over again`() {
        sync.onPreviewScrolled(previewScroll(byUser = true))
        sync.onEditorDriving()
        sync.onEditorVisibleRange(TextRange(70, 99))

        assertEquals(ScrollSync.Side.Editor, sync.driver)
        assertEquals(7.0, sync.previewLine)
    }

    @Test
    fun `the end of the page moves the editor to the end of the text`() {
        sync.onPreviewScrolled(previewScroll(byUser = true, atBottom = true))

        assertEquals(text.length..text.length, sync.editorTarget)
    }

    @Test
    fun `without a split nothing moves, and the page catches up when the split comes`() {
        sync.active = false
        sync.onEditorVisibleRange(TextRange(25, 60))
        sync.onPreviewScrolled(previewScroll(byUser = true))

        assertNull(sync.previewLine)
        assertNull(sync.editorTarget)

        sync.active = true

        assertEquals(2.5, sync.previewLine)
    }
}
