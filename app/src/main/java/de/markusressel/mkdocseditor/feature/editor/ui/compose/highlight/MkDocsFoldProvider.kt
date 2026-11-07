package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import de.markusressel.kodeeditor.library.compose.FoldRegion
import de.markusressel.kodeeditor.library.compose.KodeEditorFoldProvider
import de.markusressel.kodeeditor.library.compose.MarkdownFoldProvider

/**
 * What can be folded in a page of MkDocs: a section from its heading to the next heading of the same or a higher
 * level, the front matter, fenced code blocks and admonitions (`!!!`, `???` and `???+` with their indented body).
 *
 * The sections come from KodeEditor's [MarkdownFoldProvider], which knows nothing of front matter, so it gets the text
 * with the front matter blanked out: a YAML comment there is no heading. Folds only change what the editor shows,
 * never the text. Links are not shortened within lines, the editor shows them as they are.
 */
internal object MkDocsFoldProvider : KodeEditorFoldProvider {

    override fun foldRegions(text: String): List<FoldRegion> {
        val lines = text.split('\n')
        val regions = ArrayList<FoldRegion>()
        val frontMatterEnd = frontMatterEnd(lines)
        if (frontMatterEnd > 0) regions += FoldRegion(0, frontMatterEnd)
        regions += MarkdownFoldProvider.foldRegions(if (frontMatterEnd > 0) blankLines(text, frontMatterEnd) else text)
        blockRegions(lines, firstLine = frontMatterEnd + 1, regions)
        return regions.distinctBy { it.startLine }
    }

    /** @return the line that closes the front matter, -1 if the text has none */
    private fun frontMatterEnd(lines: List<String>): Int {
        if (lines.first().trimEnd('\r') != FRONT_MATTER_FENCE) return -1
        for (line in 1 until lines.size) {
            val content = lines[line].trimEnd('\r')
            if (content == FRONT_MATTER_FENCE || content == FRONT_MATTER_END) return line
        }
        return -1
    }

    /** @return the [text] with its lines up to [lastLine] empty, so that the lines keep their numbers */
    private fun blankLines(text: String, lastLine: Int): String {
        var end = 0
        repeat(lastLine) { end = text.indexOf('\n', end) + 1 }
        val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
        return "\n".repeat(lastLine) + text.substring(lineEnd)
    }

    private class Admonition(val start: Int, val indent: Int, var lastWithText: Int)

    private class Fence(val start: Int, val character: Char, val length: Int, val admonitions: Int)

    /** Adds the fenced code blocks and the admonitions from [firstLine] on */
    private fun blockRegions(lines: List<String>, firstLine: Int, regions: MutableList<FoldRegion>) {
        // the admonitions the current line is in, the innermost last
        val admonitions = ArrayList<Admonition>()
        var fence: Fence? = null

        fun closeAdmonition() {
            val admonition = admonitions.removeAt(admonitions.lastIndex)
            if (admonition.lastWithText > admonition.start) regions += FoldRegion(admonition.start, admonition.lastWithText)
            // a fence in it ends with it, and folds nothing
            if (fence != null && fence!!.admonitions > admonitions.size) fence = null
        }

        for (line in firstLine until lines.size) {
            val content = lines[line]
            val first = content.indexOfFirst { it != ' ' && it != '\t' }
            if (first < 0 || content[first] == '\r') continue
            val indent = columnOf(content, first)
            // a body is indented further than its marker
            while (admonitions.isNotEmpty() && indent <= admonitions.last().indent) closeAdmonition()
            admonitions.forEach { it.lastWithText = line }

            val open = fence
            if (open != null) {
                if (isFenceClose(content, first, open)) {
                    fence = null
                    // a fence with nothing in it has nothing to fold
                    if (line - open.start > 1) regions += FoldRegion(open.start, line)
                }
                continue
            }
            val length = repeatedAt(content, first)
            when {
                (content[first] == '`' || content[first] == '~') && length >= MIN_FENCE_LENGTH &&
                    // a backtick fence must not contain backticks in its info string, like in the rule book
                    (content[first] == '~' || content.indexOf('`', first + length) < 0) ->
                    fence = Fence(line, content[first], length, admonitions.size)

                admonitionMarker.matchesAt(content, first) -> admonitions += Admonition(line, indent, line)
            }
        }
        while (admonitions.isNotEmpty()) closeAdmonition()
    }

    private fun isFenceClose(content: String, first: Int, fence: Fence): Boolean {
        if (content[first] != fence.character) return false
        val length = repeatedAt(content, first)
        return length >= fence.length && content.substring(first + length).isBlank()
    }

    private fun repeatedAt(content: String, start: Int): Int {
        var index = start
        while (index < content.length && content[index] == content[start]) index++
        return index - start
    }

    /** @return the column of [index] in the [line], with tab stops every 4 columns like Python-Markdown */
    private fun columnOf(line: String, index: Int): Int {
        var column = 0
        for (i in 0 until index) column = if (line[i] == '\t') (column / TAB_SIZE + 1) * TAB_SIZE else column + 1
        return column
    }

    private val admonitionMarker = Regex("(?:!!!|\\?\\?\\?\\+?)[ \\t]+\\S")

    private const val FRONT_MATTER_FENCE = "---"
    private const val FRONT_MATTER_END = "..."
    private const val MIN_FENCE_LENGTH = 3
    private const val TAB_SIZE = 4
}
