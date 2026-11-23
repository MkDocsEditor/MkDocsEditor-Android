package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.data.persistence.SavedDocumentView
import kotlin.math.abs
import kotlin.math.floor

/**
 * Where the user was in a document, placed in its text of now
 *
 * @param editorTop the source line at the top of the editor, null if the editor was never shown
 * @param pageTop the source line at the top of the rendered page
 */
internal data class PlacedView(
    val editorTop: Double?,
    val pageTop: Double?,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * Takes the view of a document to save it. Lines and columns instead of offsets, and a line of the text near the top
 * of the view as an anchor: others may change the text until the document is opened again, and a line above that
 * grows or shrinks moves the place only by lines, which the anchor finds again.
 */
internal fun captureView(text: String, editorTop: Double?, pageTop: Double?, selectionStart: Int, selectionEnd: Int): SavedDocumentView {
    val lines = text.split('\n')
    val (startLine, startColumn) = lineAndColumn(lines, selectionStart)
    val (endLine, endColumn) = lineAndColumn(lines, selectionEnd)
    val top = floor(editorTop ?: pageTop ?: startLine.toDouble()).toInt().coerceIn(0, lines.lastIndex)
    // an empty line is everywhere, so the next line with text, or else the one before
    val anchor = (top..lines.lastIndex).firstOrNull { lines[it].isNotBlank() }
        ?: (top downTo 0).firstOrNull { lines[it].isNotBlank() }
    return SavedDocumentView(
        editorTopLine = editorTop,
        pageTopLine = pageTop,
        selectionStartLine = startLine,
        selectionStartColumn = startColumn,
        selectionEndLine = endLine,
        selectionEndColumn = endColumn,
        anchorLine = anchor ?: 0,
        anchorText = anchor?.let { lines[it] },
    )
}

/**
 * Places a saved view in the text of now: everything moves by as many lines as the anchor line moved, to the copy of
 * it that is nearest to where it was. Without that line, the view stays at its lines. What is out of the text now is
 * clamped to it.
 */
internal fun SavedDocumentView.placeIn(text: String): PlacedView {
    val lines = text.split('\n')
    val anchorText = anchorText
    val moved = if (anchorText == null) null else lines.indices
        .filter { lines[it] == anchorText }
        .minByOrNull { abs(it - anchorLine) }
    val shift = (moved ?: anchorLine) - anchorLine
    val lineCount = lines.size.toDouble()
    return PlacedView(
        editorTop = editorTopLine?.let { (it + shift).coerceIn(0.0, lineCount) },
        pageTop = pageTopLine?.let { (it + shift).coerceIn(0.0, lineCount) },
        selectionStart = offsetOf(text, lines, selectionStartLine + shift, selectionStartColumn),
        selectionEnd = offsetOf(text, lines, selectionEndLine + shift, selectionEndColumn),
    )
}

private fun lineAndColumn(lines: List<String>, offset: Int): Pair<Int, Int> {
    var start = 0
    lines.forEachIndexed { index, line ->
        if (offset <= start + line.length) return index to (offset - start).coerceAtLeast(0)
        start += line.length + 1
    }
    return lines.lastIndex to lines.last().length
}

private fun offsetOf(text: String, lines: List<String>, line: Int, column: Int): Int {
    if (line < 0) return 0
    if (line > lines.lastIndex) return text.length
    val index = line
    val start = (0 until index).sumOf { lines[it].length + 1 }
    val offset = start + column.coerceIn(0, lines[index].length)
    // never between the two halves of an emoji
    return if (offset in 1 until text.length && text[offset - 1].isHighSurrogate() && text[offset].isLowSurrogate()) offset - 1 else offset
}
