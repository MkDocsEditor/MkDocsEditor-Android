package de.markusressel.mkdocseditor.feature.editor.domain

import androidx.compose.ui.text.TextRange

/**
 * A block of the rendered page that knows its source line (`data-source-line`, 0-based), with its edges in CSS pixels
 * from the top of the visible part of the page
 */
internal data class PreviewBlock(val line: Int, val top: Float, val bottom: Float)

/**
 * Where the rendered page is scrolled to, as the page script reports it
 *
 * @param blocks the blocks in and around the visible part, in document order: the last one that starts above it, all
 * that start in it and the first one below it
 * @param height of the visible part, 0 while the page has no room
 * @param byUser whether a finger or a wheel moves the page, and not the app
 */
internal data class PreviewScroll(
    val blocks: List<PreviewBlock>,
    val height: Float,
    val scrollY: Float,
    val atTop: Boolean,
    val atBottom: Boolean,
    val byUser: Boolean,
)

/**
 * The source lines that the page shows: from the first block whose top is visible to the last block whose bottom is
 * visible. A block ends where the next one with a later line starts, the last one at the end of the text. Same in the
 * web client.
 *
 * @return 0-based lines, the end can be behind the last line of the text. Null while the page has no room or no blocks.
 */
internal fun visibleSourceLines(scroll: PreviewScroll): IntRange? {
    val blocks = scroll.blocks
    if (scroll.height <= 0f || blocks.isEmpty()) return null
    val height = scroll.height
    // a block that is taller than the view: the one that reaches over the edge counts
    val topIndex = blocks.indexOfFirst { it.top >= 0f && it.top < height }
        .takeIf { it >= 0 } ?: blocks.indexOfLast { it.top < 0f }.coerceAtLeast(0)
    val bottomIndex = blocks.indexOfLast { it.bottom > 0f && it.bottom <= height }
        .takeIf { it >= 0 } ?: blocks.indexOfLast { it.top < height }.coerceAtLeast(0)
    // the bottom of one block and the top of the next one are visible, with neither one in full: both count
    val first = blocks[minOf(topIndex, bottomIndex)].line
    return first..endLineOf(blocks, maxOf(topIndex, bottomIndex))
}

private fun endLineOf(blocks: List<PreviewBlock>, index: Int): Int {
    val line = blocks[index].line
    val next = blocks.subList(index + 1, blocks.size).firstOrNull { it.line > line } ?: return Int.MAX_VALUE
    return next.line - 1
}

/**
 * The viewport of source lines, as the code editor sends it: from the start of the first line to the end of the last
 * one. Same in the web client.
 *
 * @return top and bottom as indices of the text
 */
internal fun viewportOfLines(text: String, lines: IntRange): Pair<Int, Int> {
    val starts = lineStarts(text)
    val first = lines.first.coerceIn(0, starts.lastIndex)
    val last = lines.last.coerceIn(first, starts.lastIndex)
    return starts[first] to lineEnd(text, starts, last)
}

/**
 * The source line at the top of the visible part of the page, with the part of the way to the next block, so that
 * scrolling the page moves the editor along smoothly
 */
internal fun topSourceLine(scroll: PreviewScroll, lineCount: Int): Double {
    val blocks = scroll.blocks
    if (scroll.atTop || blocks.isEmpty()) return 0.0
    val aboveIndex = blocks.indexOfLast { it.top <= 0f }
    if (aboveIndex < 0) {
        // above the first block, from the start of the page to it
        val first = blocks.first()
        val distance = scroll.scrollY + first.top
        return if (distance > 0f) first.line * (scroll.scrollY / distance).toDouble() else 0.0
    }
    // the first one of a line: a block and the first block in it start at the same line and place
    val above = blocks.subList(0, aboveIndex + 1).first { it.line == blocks[aboveIndex].line }
    val next = blocks.subList(aboveIndex + 1, blocks.size).firstOrNull { it.line > above.line && it.top > above.top }
    val line = if (next != null) {
        above.line + (-above.top / (next.top - above.top)).toDouble() * (next.line - above.line)
    } else {
        val tall = (above.bottom - above.top).coerceAtLeast(1f)
        above.line + (-above.top / tall).coerceAtMost(1f).toDouble() * (lineCount - above.line)
    }
    return line.coerceIn(0.0, lineCount.toDouble())
}

/** the number of lines of the text, an empty text has one */
internal fun lineCountOf(text: String): Int = text.count { it == '\n' } + 1

/** @return the index of the text at a line with a fraction, like 2.5 for the middle of the third line */
internal fun positionOfLine(text: String, line: Double): Int {
    val starts = lineStarts(text)
    if (line <= 0.0) return 0
    val whole = line.toInt()
    if (whole > starts.lastIndex) return text.length
    val start = starts[whole]
    val next = if (whole + 1 <= starts.lastIndex) starts[whole + 1] else text.length
    return (start + (line - whole) * (next - start)).toInt().coerceIn(start, text.length)
}

/** @return the line of an index of the text, with the part of the way through it, the reverse of [positionOfLine] */
internal fun lineOfPosition(text: String, position: Int): Double {
    val starts = lineStarts(text)
    val index = position.coerceIn(0, text.length)
    val line = starts.indexOfLast { it <= index }
    val start = starts[line]
    val next = if (line + 1 <= starts.lastIndex) starts[line + 1] else text.length
    return if (next > start) line + (index - start).toDouble() / (next - start) else line.toDouble()
}

private fun lineStarts(text: String): List<Int> = buildList {
    add(0)
    text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
}

/** the index of the line break that ends the line, the end of the text for the last one */
private fun lineEnd(text: String, starts: List<Int>, line: Int): Int =
    if (line + 1 <= starts.lastIndex) starts[line + 1] - 1 else text.length

/**
 * What this user looks at, for those who follow them: the text of the code editor while it is shown, else the lines of
 * the rendered page.
 *
 * @param editorRange the text the editor shows, its end exclusive
 * @param previewLines the source lines the rendered page shows, see [visibleSourceLines]
 * @return top and bottom as indices of the first and the last character, null for none
 */
internal fun localViewport(editorShown: Boolean, editorRange: TextRange?, previewLines: IntRange?, text: String): Pair<Int, Int>? =
    if (editorShown) {
        editorRange?.takeUnless { it.collapsed }?.let { it.start to it.end - 1 }
    } else {
        previewLines?.let { viewportOfLines(text, it) }
    }
