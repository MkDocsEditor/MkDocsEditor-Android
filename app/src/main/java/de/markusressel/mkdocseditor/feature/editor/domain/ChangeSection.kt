package de.markusressel.mkdocseditor.feature.editor.domain

/**
 * The section of a page that a change was in, from its heading to the next heading of any level, because the backend
 * counts a line to the nearest heading above it
 *
 * @param startLine the line of the heading, 0-based
 * @param endLine the line behind the section, 0-based, the line count at the end of the text
 */
data class ChangeSection(val startLine: Int, val endLine: Int) {

    companion object {
        /** the text of an ATX heading as the backend names it in a change summary (`internal/activity/summary.go`) */
        private val rawHeading = Regex("^ {0,3}#{1,6}(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$")

        /**
         * @param text the current text of the page
         * @param headings the headings of a change summary, in the order the backend sends them
         * @return the section of the first of [headings] that the text still has, the first one of several with the same
         * name, null if the text has none of them
         */
        fun find(text: String, headings: List<String>): ChangeSection? {
            if (headings.isEmpty()) return null
            val found = Headings.of(text)
            if (found.isEmpty()) return null
            val lines = text.split('\n')
            // the backend sends the heading as written, the app reads it without inline Markdown; either matches
            fun names(heading: Heading): Set<String> = setOfNotNull(
                heading.text,
                lines.getOrNull(heading.line - 1)?.let { rawHeading.find(it.trimEnd('\r'))?.groupValues?.get(1)?.trim() },
            )
            val named = found.map { it to names(it) }
            for (wanted in headings) {
                val name = wanted.trim()
                val index = named.indexOfFirst { (_, names) -> name in names }
                if (index < 0) continue
                val start = found[index].line - 1
                val end = found.getOrNull(index + 1)?.let { it.line - 1 } ?: lines.size
                return ChangeSection(start, end)
            }
            return null
        }
    }
}
