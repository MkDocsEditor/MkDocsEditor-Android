package de.markusressel.mkdocseditor.feature.editor.domain

import java.security.MessageDigest

/**
 * Lines of the text, 0-based
 *
 * @param start the first line
 * @param end the line behind the last one. The same as [start] for the place of removed lines in front of [start],
 * which is the line count at the end of the text.
 */
data class LineRange(val start: Int, val end: Int) {
    /** removed lines, with nothing in their place */
    val isRemoval: Boolean get() = start == end
}

/**
 * Where a page opened at a change goes, and what lights up there
 *
 * @param line the 0-based line that goes to the top of the view
 * @param ranges the changed lines, in the order of the text
 * @param exact whether the text is the one the change was made on, so a later text can not do better
 */
data class ChangeTarget(val line: Int, val ranges: List<LineRange>, val exact: Boolean = true) {

    companion object {
        /**
         * Finds a change in the current text, see "Changed lines" in `docs/dashboard.md` of the backend: the lines as
         * they are if the text is the one after the edit, else relative to their heading, else as they are, clamped to
         * the text. An entry from before the backend sent lines goes to the section of its first heading the text
         * still has.
         *
         * @return null if the page stays at the top: no line changed but the newline at the end, the text is empty, or
         * none of the headings of an older entry is there
         */
        fun of(text: String, change: ChangeLocation): ChangeTarget? {
            val ranges = change.ranges ?: return ofHeadings(text, change.headings)
            val lineCount = SummaryText.lines(text).size
            if (ranges.isEmpty() || lineCount == 0) return null
            val exact = change.textHash != null && change.textHash == SummaryText.hash(text)
            val headings = if (exact) emptyList() else SummaryText.headings(text)
            val found = ranges.map { range ->
                val line = when {
                    exact -> range.line
                    range.heading != null -> {
                        val same = headings.filter { it.title == range.heading }
                        val heading = same.getOrNull(range.headingOccurrence - 1) ?: same.firstOrNull()
                        heading?.let { it.index + 1 + range.offset } ?: range.line
                    }
                    else -> range.line
                }
                if (range.count == 0) {
                    val start = (line - 1).coerceIn(0, lineCount)
                    LineRange(start, start)
                } else {
                    val start = (line - 1).coerceIn(0, lineCount - 1)
                    LineRange(start, (start + range.count).coerceAtMost(lineCount))
                }
            }
            return ChangeTarget(found.first().start.coerceAtMost(lineCount - 1), found, exact)
        }

        /**
         * The section of the first heading that the text still has, the first one of several with the same name: from
         * that heading to the next heading of any level, because the backend counts a line to the nearest heading above
         * it
         *
         * @param headings the headings of a change summary, in the order the backend sends them
         */
        fun ofHeadings(text: String, headings: List<String>): ChangeTarget? {
            if (headings.isEmpty()) return null
            val found = SummaryText.headings(text)
            val lineCount = SummaryText.lines(text).size
            for (wanted in headings) {
                val index = found.indexOfFirst { it.title == wanted }
                if (index < 0) continue
                val start = found[index].index
                val end = found.getOrNull(index + 1)?.index ?: lineCount
                return ChangeTarget(start, listOf(LineRange(start, end)))
            }
            return null
        }
    }
}

/**
 * The text the way the backend reads it for a change summary (`internal/activity/summary.go`), so that lines, headings
 * and the hash agree with it
 */
internal object SummaryText {

    /** a heading, [index] its 0-based line */
    data class SummaryHeading(val index: Int, val title: String)

    private val heading = Regex("^ {0,3}#{1,6}(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$")
    private val fence = Regex("^ {0,3}(```|~~~)")

    /** split on `\n` only; a newline at the end starts no line, and an empty text has none */
    fun lines(text: String): List<String> =
        if (text.isEmpty()) emptyList() else text.removeSuffix("\n").split('\n')

    /** the first 16 hex digits of SHA-256 over the UTF-8 bytes of the text */
    fun hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
        .take(16)

    /** ATX headings outside of fences and of the front matter, with their text as the backend names them */
    fun headings(text: String): List<SummaryHeading> {
        val frontMatter = frontMatterLines(text)
        var open: String? = null
        val result = mutableListOf<SummaryHeading>()
        lines(text).forEachIndexed { i, line ->
            if (i < frontMatter) return@forEachIndexed
            val marker = fence.find(line)?.groupValues?.get(1)
            if (marker != null) {
                // like the backend: any line that starts with the marker closes the fence
                open = when (open) {
                    null -> marker
                    marker -> null
                    else -> open
                }
                return@forEachIndexed
            }
            if (open != null) return@forEachIndexed
            val title = heading.find(line.trimEnd('\r'))?.groupValues?.get(1)?.trim()
            if (!title.isNullOrEmpty()) result += SummaryHeading(i, title)
        }
        return result
    }

    /** the lines of the front matter with both of its delimiters, 0 without one (`wiki.FrontMatterLines`) */
    internal fun frontMatterLines(text: String): Int {
        val lines = text.removePrefix("﻿").split('\n')
        if (lines.size < 2 || lines[0].trimEnd(' ', '\t', '\r') != "---") return 0
        for (i in 1 until lines.size) {
            val line = lines[i].trimEnd(' ', '\t', '\r')
            if (line == "---" || line == "...") return i + 1
        }
        return 0
    }
}
