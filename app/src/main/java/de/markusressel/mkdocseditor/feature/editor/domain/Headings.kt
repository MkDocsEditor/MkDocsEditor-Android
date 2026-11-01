package de.markusressel.mkdocseditor.feature.editor.domain

import java.text.Normalizer

/**
 * A heading of a page
 *
 * @param id the anchor MkDocs gives the heading (`toc` extension defaults, or `{#id}`)
 * @param line 1-based
 */
data class Heading(val level: Int, val text: String, val id: String, val line: Int)

/**
 * The headings of a page and their anchors, the same way the web client finds them
 * (`src/markdown/headings.ts`)
 */
object Headings {

    /**
     * The default slug of Python-Markdown's `toc` extension, which MkDocs uses: accents
     * removed, other non-ASCII dropped, lower case, runs of spaces and dashes become one dash
     */
    fun slugify(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKD)
        .replace(Regex("[^\\x00-\\x7f]"), "")
        .replace(Regex("[^\\w\\s-]"), "")
        .trim()
        .lowercase()
        .replace(Regex("[-\\s]+"), "-")

    /** inline Markdown of a heading as plain text (what the slug is made of) */
    private fun plain(text: String): String = text
        .replace(Regex("!?\\[([^\\]]*)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("[*_~`]"), "")
        .replace(Regex("<[^>]+>"), "")
        .trim()

    private val headingStart = Regex("^ {0,3}#{1,6}(\\s|$)")
    private val heading = Regex("^ {0,3}(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$")
    private val attribute = Regex("\\s*\\{[^}]*#([\\w-]+)[^}]*\\}\\s*$")

    /** the headings of a page with their anchors, skipping code blocks and front matter */
    fun of(markdown: String): List<Heading> {
        val masked = maskNonText(markdown).split('\n')
        val source = markdown.split('\n')
        val used = HashMap<String, Int>()
        val result = mutableListOf<Heading>()
        source.forEachIndexed { i, line ->
            if (!headingStart.containsMatchIn(masked[i])) return@forEachIndexed
            val match = heading.find(line) ?: return@forEachIndexed
            var text = match.groupValues[2]
            var id = ""
            attribute.find(text)?.let {
                id = it.groupValues[1]
                text = text.substring(0, it.range.first)
            }
            text = plain(text)
            if (id.isEmpty()) {
                val base = slugify(text).ifEmpty { "section" }
                val count = used[base] ?: 0
                used[base] = count + 1
                id = if (count == 0) base else "${base}_$count"
            }
            result += Heading(match.groupValues[1].length, text, id, i + 1)
        }
        return result
    }

    /**
     * Blanks out front matter, HTML comments, fenced code and inline code, keeping the line
     * structure, so only real Markdown matches
     */
    internal fun maskNonText(text: String): String {
        fun blank(s: String) = s.replace(Regex("[^\\n]"), " ")
        var masked = text
        Regex("^---\\r?\\n[\\s\\S]*?\\r?\\n(?:---|\\.\\.\\.)(?:\\r?\\n|$)").find(masked)?.let {
            masked = blank(it.value) + masked.substring(it.value.length)
        }
        masked = Regex("<!--[\\s\\S]*?(?:-->|$)").replace(masked) { blank(it.value) }
        val lines = masked.split('\n').toMutableList()
        var fenceChar: Char? = null
        var fenceLength = 0
        val open = Regex("^\\s*(`{3,}|~{3,})")
        val close = Regex("^\\s*(`{3,}|~{3,})\\s*$")
        for (i in lines.indices) {
            val line = lines[i]
            if (fenceChar != null) {
                val c = close.find(line)
                lines[i] = blank(line)
                if (c != null && c.groupValues[1][0] == fenceChar && c.groupValues[1].length >= fenceLength) fenceChar = null
            } else {
                open.find(line)?.let {
                    fenceChar = it.groupValues[1][0]
                    fenceLength = it.groupValues[1].length
                    lines[i] = blank(line)
                }
            }
        }
        masked = lines.joinToString("\n")
        return Regex("(`+)(?!`)[\\s\\S]*?(?<!`)\\1(?!`)").replace(masked) { blank(it.value) }
    }
}
