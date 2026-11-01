package de.markusressel.mkdocseditor.feature.editor.domain

/**
 * Converts the positions of the reference index of the backend (1-based lines, 1-based
 * columns counted in code points) to offsets in the text
 */
object TextPositions {

    /**
     * The offset of a position in [text], clamped to the text (the index may be a little
     * older than the text in the editor)
     */
    fun offsetOf(text: String, line: Int, column: Int): Int {
        var offset = 0
        repeat((line - 1).coerceAtLeast(0)) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
        }
        val lineEnd = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
        var remaining = (column - 1).coerceAtLeast(0)
        while (remaining > 0 && offset < lineEnd) {
            offset += Character.charCount(text.codePointAt(offset))
            remaining--
        }
        return offset.coerceAtMost(lineEnd)
    }

    /**
     * The range of [destination] at a position; only the start when the text there differs
     */
    fun rangeOf(text: String, line: Int, column: Int, destination: String): IntRange {
        val start = offsetOf(text, line, column)
        val end = if (text.startsWith(destination, start)) start + destination.length else start
        return start until end
    }
}
