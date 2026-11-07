package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.math.floor

/** A block of the rendered page with its source line (0-based) and its top in CSS pixels from the start of the page */
internal data class PreviewLayoutBlock(val line: Int, val top: Float)

/**
 * Where the blocks of the rendered page are, as the page script reports it when its content or its size changes
 *
 * @param blocks all blocks with a source line, in document order
 * @param scrollHeight the height of the whole page
 */
internal data class PreviewLayout(val blocks: List<PreviewLayoutBlock>, val scrollHeight: Float)

/**
 * A place in the source, a line with the part of the way to the next one, at a height of a view
 *
 * @param viewportFraction the height of the view, from 0 for its top edge to 1 for its bottom edge
 */
internal data class SyncAnchor(val line: Double, val viewportFraction: Float)

/**
 * Maps the source lines to the heights of the rendered page, piecewise-linear: each block runs from its first line to
 * the first line of the next block, and from its top to the top of the next block. Line 0 is at the start of the page,
 * the end of the text at the end of the page. A nested block that starts at the line of the one around it, or above
 * it, adds nothing. preview.js maps the other way with the same points, and the web client does the same.
 *
 * @param lineCount the number of lines of the text, see [lineCountOf]
 */
internal class SourceHeightMap(layout: PreviewLayout, val lineCount: Int) {
    private val lines: IntArray
    private val heights: FloatArray

    init {
        val lines = mutableListOf(0)
        val heights = mutableListOf(0f)
        for (block in layout.blocks) {
            // a block behind the end of the text belongs to a page that is not up to date yet
            if (block.line <= lines.last() || block.line >= lineCount || block.top < heights.last()) continue
            lines += block.line
            heights += block.top
        }
        lines += lineCount.coerceAtLeast(lines.last() + 1)
        heights += layout.scrollHeight.coerceAtLeast(heights.last())
        this.lines = lines.toIntArray()
        this.heights = heights.toFloatArray()
    }

    /** @return the source line with a fraction at a height of the page, from 0 to [lineCount] */
    fun lineAt(height: Float): Double {
        if (height <= 0f) return 0.0
        if (height >= heights.last()) return lines.last().toDouble()
        // the last point at or above the height
        var low = 0
        var high = heights.lastIndex
        while (high - low > 1) {
            val middle = (low + high) ushr 1
            if (heights[middle] <= height) low = middle else high = middle
        }
        val span = heights[high] - heights[low]
        if (span <= 0f) return lines[low].toDouble()
        return lines[low] + (height - heights[low]) / span.toDouble() * (lines[high] - lines[low])
    }
}

/** @return how far a view is scrolled, from 0 at the top to 1 at the bottom, 0 when it can't scroll */
internal fun scrollProgress(offset: Float, maxOffset: Float): Float =
    if (maxOffset <= 0f) 0f else (offset / maxOffset).coerceIn(0f, 1f)

/**
 * The reading line of the page: at the height of the view that is as far down as the page is scrolled, so that the
 * top line is at the top of the page and the end of the text at its end
 */
internal fun previewReadingAnchor(map: SourceHeightMap, scroll: PreviewScroll): SyncAnchor {
    val progress = scrollProgress(scroll.scrollY, scroll.scrollHeight - scroll.height)
    return SyncAnchor(map.lineAt(scroll.scrollY + progress * scroll.height), progress)
}

/** What the scroll sync reads of the view of the code editor, in a snapshot */
internal interface EditorScrollView {
    /** in pixels, null before the first layout */
    val scrollOffset: Float?

    /** in pixels, 0 if the whole text fits, null before the first layout */
    val maxScrollOffset: Float?

    /** @return the source line with a fraction at a height of the view, null before the first layout */
    fun lineAt(viewportFraction: Float): Double?
}

/** The reading line of the editor, at the height of its view that is as far down as it is scrolled */
internal fun editorReadingAnchor(view: EditorScrollView): SyncAnchor? {
    val progress = scrollProgress(view.scrollOffset ?: return null, view.maxScrollOffset ?: return null)
    return SyncAnchor(view.lineAt(progress) ?: return null, progress)
}

/**
 * The caret at its height in the editor, so that the page shows the block being typed at the same height
 *
 * @param caretLine the source line of the caret with the part of the way through it, see [lineOfPosition]
 * @return null if the caret is out of view
 */
internal fun editorCaretAnchor(view: EditorScrollView, caretLine: Double): SyncAnchor? {
    val top = view.lineAt(0f) ?: return null
    val bottom = view.lineAt(1f) ?: return null
    if (caretLine < top || caretLine > bottom) return null
    // the lines only grow down the view
    var low = 0f
    var high = 1f
    repeat(CARET_SEARCH_STEPS) {
        val middle = (low + high) / 2
        if ((view.lineAt(middle) ?: return null) < caretLine) low = middle else high = middle
    }
    return SyncAnchor(caretLine, high)
}

/** Where the editor should put a line, with the part of the way down it, see `KodeEditorViewState.scrollToLine` */
internal data class EditorTarget(val line: Int, val fraction: Float, val viewportFraction: Float)

/** @return the place in the editor for an anchor, the end of the text as the bottom of its last line */
internal fun editorTargetOf(anchor: SyncAnchor, lineCount: Int): EditorTarget {
    val line = anchor.line.coerceIn(0.0, lineCount.toDouble())
    if (line >= lineCount) return EditorTarget(lineCount - 1, 1f, anchor.viewportFraction)
    val whole = floor(line)
    return EditorTarget(whole.toInt(), (line - whole).toFloat(), anchor.viewportFraction)
}

// 2^-16 of the view is below a pixel
private const val CARET_SEARCH_STEPS = 16
