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
    fun `off means no pane drives`() {
        sync.enabled = false
        editorMoves()
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        sync.onLocalEdit()
        editorMoves(caretLine = 3.0)

        assertNull(sync.previewTarget)
        assertNull(sync.editorTarget)
        assertEquals(0, sync.previewMovedByUser)
    }

    @Test
    fun `turning it off lets go of the page, so that it stays where it is`() {
        editorMoves()
        sync.enabled = false

        assertNull(sync.previewTarget)
    }

    @Test
    fun `turning it on aligns the page with the editor once, also after the page drove`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        sync.enabled = false
        // the user moves both on their own
        editorMoves()

        sync.enabled = true

        assertEquals(ScrollSync.Side.Editor, sync.driver)
        assertReadingLine(sync.previewTarget)

        // from then on the side the user moves drives again
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        assertEquals(EditorTarget(line = 6, fraction = 0f, viewportFraction = 0.5f), sync.editorTarget)
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

    /**
     * An editor whose folds hide lines, like KodeEditor's: it only has rows for the [shown] lines, 100 pixels each, and
     * shows three of them from the row [top]. Its scroll offsets are those of the rows that are shown.
     */
    private class FoldedEditor(private val shown: List<Int>, var top: Double) : EditorScrollView {
        override val scrollOffset get() = (top * 100).toFloat()
        override val maxScrollOffset get() = (shown.size - 3) * 100f
        override fun lineAt(viewportFraction: Float): Double {
            val row = top + viewportFraction * 3
            val index = row.toInt().coerceAtMost(shown.lastIndex)
            // the bottom of the view is the bottom of the last row that is shown
            return shown[index] + (row - index).coerceAtMost(1.0)
        }
    }

    private fun foldedEditorMoves(editor: FoldedEditor) = sync.onEditorAnchor(sync.editorAnchorOf(editor) { 0.0 })

    @Test
    fun `with the last section folded, the bottom of the editor is still the end of the page`() {
        // line 5 folds the lines 6 to 9, the end of the text
        val folded = FoldedEditor(shown = (0..5).toList(), top = 3.0)
        foldedEditorMoves(folded)

        assertEquals(SyncAnchor(10.0, 1f), sync.previewTarget?.anchor)
    }

    @Test
    fun `the page jumps over a folded section when the editor passes its line`() {
        // line 2 folds the lines 3 to 5
        val folded = FoldedEditor(shown = listOf(0, 1, 2, 6, 7, 8, 9), top = 1.7)
        foldedEditorMoves(folded)
        // the reading line is near the bottom of line 2
        assertAnchor(SyncAnchor(2.975, 0.425f), sync.previewTarget?.anchor)

        folded.top = 1.8
        foldedEditorMoves(folded)
        // and a little further down it is past the fold
        assertAnchor(SyncAnchor(6.15, 0.45f), sync.previewTarget?.anchor)
    }

    @Test
    fun `while the page scrolls through a folded section, the editor at the fold does not move the page back`() {
        sync.onPreviewScrolled(previewScroll(scrollY = 400f, byUser = true))
        // line 6 is in a fold of line 5, so the editor stays at line 5
        assertEquals(6, sync.editorTarget?.line)
        val folded = FoldedEditor(shown = listOf(0, 1, 2, 3, 4, 5, 9), top = 3.0)
        foldedEditorMoves(folded)

        assertEquals(ScrollSync.Side.Preview, sync.driver)
        assertNull(sync.previewTarget)
    }

    private fun restoredSync(positions: ScrollSync.Positions) = ScrollSync(text = { text }, now = { time }, restored = positions).apply {
        active = true
        onPreviewLayout(
            PreviewLayout(
                listOf(PreviewLayoutBlock(2, 100f), PreviewLayoutBlock(5, 400f), PreviewLayoutBlock(8, 700f)),
                scrollHeight = 1000f,
            )
        )
    }

    @Test
    fun `it keeps the source line at the top of both sides, for a recreation of the screen`() {
        sync.onEditorTop(3.0)
        sync.onPreviewScrolled(previewScroll(400f, byUser = true))

        assertEquals(ScrollSync.Positions(editorTop = 3.0, pageTop = 5.0), sync.positions)
    }

    @Test
    fun `after a recreation the page goes back to its line at the top, and the editor to its own`() {
        val restored = restoredSync(ScrollSync.Positions(editorTop = 3.0, pageTop = 5.0))

        assertAnchor(SyncAnchor(5.0, 0f), restored.previewTarget?.anchor)
        assertEquals(false, restored.previewTarget?.smooth)
        assertEquals(3.0, restored.editorRestore)
    }

    @Test
    fun `the editor does not drive the page until it is back at its line`() {
        val restored = restoredSync(ScrollSync.Positions(editorTop = 3.0, pageTop = 5.0))

        // the first layouts of the new editor are at the top
        restored.onEditorTop(0.0)
        restored.onEditorAnchor(restored.editorAnchorOf(FakeEditor(top = 0.0)) { 0.0 })
        assertAnchor(SyncAnchor(5.0, 0f), restored.previewTarget?.anchor)
        assertEquals(3.0, restored.positions.editorTop)

        restored.onEditorRestored()
        restored.onEditorAnchor(restored.editorAnchorOf(editor) { 0.0 })
        assertReadingLine(restored.previewTarget)
    }

    @Test
    fun `a page that has just loaded does not count as scrolled to the top`() {
        val restored = restoredSync(ScrollSync.Positions(editorTop = null, pageTop = 5.0))

        restored.onPreviewScrolled(previewScroll(0f, byUser = false))

        assertEquals(5.0, restored.positions.pageTop)
    }

    @Test
    fun `the user scrolling the page ends its way back, so that a new load does not go there again`() {
        val restored = restoredSync(ScrollSync.Positions(editorTop = null, pageTop = 5.0))

        restored.onPreviewScrolled(previewScroll(250f, byUser = true))

        assertNull(restored.previewTarget)
        assertEquals(3.5, assertNotNull(restored.positions.pageTop), 0.0001)
    }

    @Test
    fun `the positions survive the saved state, also a side that was never laid out`() {
        sync.onPreviewScrolled(previewScroll(400f, byUser = false))
        val saver = ScrollSync.saver(text = { text })
        val scope = object : androidx.compose.runtime.saveable.SaverScope {
            override fun canBeSaved(value: Any) = true
        }

        val saved = assertNotNull(with(saver) { scope.save(sync) })
        val restored = saver.restore(saved)

        assertEquals(ScrollSync.Positions(editorTop = null, pageTop = 5.0), assertNotNull(restored).positions)
    }
}
