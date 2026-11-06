package de.markusressel.mkdocseditor.feature.editor.ui.compose

import de.markusressel.mkdocseditor.feature.editor.domain.EditorScrollView
import de.markusressel.mkdocseditor.feature.editor.domain.EditorTarget
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLayout
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLayoutBlock
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewScroll
import de.markusressel.mkdocseditor.feature.editor.domain.SyncAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ScrollSyncTest {

    // ten lines
    private val text = (0 until 10).joinToString("\n") { "line $it" }
    private var time = 1_000L
    private val sync = ScrollSync(text = { text }, now = { time }).apply {
        active = true
        // blocks at lines 2, 5 and 8, on a page of 1000 pixels
        onPreviewLayout(
            PreviewLayout(
                listOf(PreviewLayoutBlock(2, 100f), PreviewLayoutBlock(5, 400f), PreviewLayoutBlock(8, 700f)),
                scrollHeight = 1000f,
            )
        )
    }

    /** shows five lines from [top], and can scroll by 100 pixels for each line it hides */
    private class FakeEditor(var top: Double) : EditorScrollView {
        override val scrollOffset get() = (top * 100).toFloat()
        override val maxScrollOffset get() = 500f
        override fun lineAt(viewportFraction: Float) = top + viewportFraction * 5
    }

    private val editor = FakeEditor(top = 2.0)

    private fun editorMoves(caretLine: Double = 0.0) = sync.onEditorAnchor(sync.editorAnchorOf(editor) { caretLine })

    private fun previewScroll(scrollY: Float, byUser: Boolean) =
        PreviewScroll(emptyList(), height = 200f, scrollY = scrollY, scrollHeight = 1000f, byUser = byUser)

    private fun assertAnchor(expected: SyncAnchor, actual: SyncAnchor?) {
        assertEquals(expected.line, assertNotNull(actual).line, 0.0001)
        assertEquals(expected.viewportFraction, actual.viewportFraction, 0.0001f)
    }

    /** the reading line of the editor at its top line 2: scrolled 40 %, at line 4 */
    private fun assertReadingLine(target: ScrollSync.PreviewTarget?) {
        assertAnchor(SyncAnchor(4.0, 0.4f), assertNotNull(target).anchor)
        assertEquals(false, target.smooth)
    }

    @Test
    fun `the editor drives the page at first, with its reading line at the same height`() {
        editorMoves()

        assertReadingLine(sync.previewTarget)
        assertNull(sync.editorTarget)
    }

    @Test
    fun `the top and the bottom of the editor are the top and the bottom of the page`() {
        editor.top = 0.0
        editorMoves()
        assertEquals(SyncAnchor(0.0, 0f), sync.previewTarget?.anchor)

        editor.top = 5.0
        editorMoves()
        assertEquals(SyncAnchor(10.0, 1f), sync.previewTarget?.anchor)
    }

    @Test
    fun `scrolling the page by hand puts its reading line at the same height of the editor`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))

        assertEquals(ScrollSync.Side.Preview, sync.driver)
        // the reading line is half way down the page at 500, a third of the way from line 5 to line 8
        assertEquals(EditorTarget(line = 6, fraction = 0f, viewportFraction = 0.5f), sync.editorTarget)
    }

    @Test
    fun `the top and the bottom of the page are the top and the bottom of the editor`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 0f, byUser = true))
        assertEquals(EditorTarget(0, 0f, 0f), sync.editorTarget)

        sync.onPreviewScrolled(previewScroll(scrollY = 800f, byUser = true))
        assertEquals(EditorTarget(9, 1f, 1f), sync.editorTarget)
    }

    @Test
    fun `while the page drives, the editor moving along does not move the page back`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        editor.top = 4.0
        editorMoves()

        assertNull(sync.previewTarget)
    }

    @Test
    fun `when the page takes over, the anchor of the editor is gone and does not pull the page back`() {
        editorMoves()
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))

        assertNull(sync.previewTarget)

        // the page goes away and comes back, and stays where the user left it
        sync.active = false
        sync.active = true

        assertNull(sync.previewTarget)
    }

    @Test
    fun `a scroll of the page that the app made does not drive the editor`() {
        editorMoves()
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = false))

        assertEquals(ScrollSync.Side.Editor, sync.driver)
        assertNull(sync.editorTarget)
    }

    @Test
    fun `moving the editor by hand takes over again`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        sync.onEditorDriving()
        editor.top = 3.0
        editorMoves()

        assertEquals(ScrollSync.Side.Editor, sync.driver)
        assertAnchor(SyncAnchor(6.0, 0.6f), sync.previewTarget?.anchor)
    }

    @Test
    fun `right after an edit the caret is the anchor, at its height in the editor`() {
        sync.onLocalEdit()
        time += 100
        editorMoves(caretLine = 5.0)

        // the caret is three of the five lines down the editor
        assertAnchor(SyncAnchor(5.0, 0.6f), sync.previewTarget?.anchor)
        assertEquals(true, sync.previewTarget?.smooth)
    }

    @Test
    fun `typing takes over from the page`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        sync.onLocalEdit()
        editorMoves(caretLine = 5.0)

        assertEquals(ScrollSync.Side.Editor, sync.driver)
        assertEquals(5.0, sync.previewTarget?.anchor?.line)
    }

    @Test
    fun `300 ms after an edit the reading line is the anchor again`() {
        sync.onLocalEdit()
        time += ScrollSync.CARET_ANCHOR_MS
        editorMoves(caretLine = 5.0)

        assertReadingLine(sync.previewTarget)
    }

    @Test
    fun `a caret out of view leaves the reading line as the anchor`() {
        sync.onLocalEdit()
        editorMoves(caretLine = 9.0)

        assertReadingLine(sync.previewTarget)
    }

    @Test
    fun `without a split nothing moves, and the page catches up when the split comes`() {
        sync.active = false
        editorMoves()
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))

        assertNull(sync.previewTarget)
        assertNull(sync.editorTarget)

        sync.active = true

        assertReadingLine(sync.previewTarget)
    }
}
