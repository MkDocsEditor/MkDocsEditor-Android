package de.markusressel.mkdocseditor.feature.search.domain

import de.markusressel.mkdocseditor.feature.editor.domain.Headings

/**
 * The lines of a page that match a search, short enough to show the match at a glance
 */
internal object SearchExcerpts {

    private const val CHARS_BEFORE_MATCH = 20
    private const val CHARS_AFTER_MATCH = 70

    /** list, quote and heading markers, which say nothing about the content */
    private val lineMarker = Regex("^\\s*(#{1,6}\\s+|[-*+]\\s+|>\\s*|\\d+\\.\\s+)?")

    data class Result(
        val matchCount: Int,
        val excerpts: List<SearchResultItem.Document.ExcerptData>,
    )

    fun find(content: String, searchTerm: String): Result {
        if (searchTerm.isEmpty()) return Result(0, emptyList())
        val regex = searchTerm.toSearchRegex()
        val headings = Headings.of(content)

        var matchCount = 0
        val excerpts = content.split('\n').mapIndexedNotNull { index, line ->
            val matches = regex.findAll(line).map { it.range }.toList()
            if (matches.isEmpty()) return@mapIndexedNotNull null
            matchCount += matches.size

            val lineNumber = index + 1
            excerpt(
                line = line,
                matches = matches,
                heading = headings.takeUnless { it.any { heading -> heading.line == lineNumber } }
                    ?.lastOrNull { it.line < lineNumber }?.text,
            )
        }
        return Result(matchCount, excerpts)
    }

    private fun excerpt(
        line: String,
        matches: List<IntRange>,
        heading: String?,
    ): SearchResultItem.Document.ExcerptData {
        val contentStart = lineMarker.find(line)?.range?.last?.plus(1) ?: 0
        val contentEnd = line.trimEnd().length
        val first = matches.first()

        var start = (first.first - CHARS_BEFORE_MATCH).coerceAtLeast(contentStart.coerceAtMost(first.first))
        var end = (first.last + 1 + CHARS_AFTER_MATCH).coerceAtMost(contentEnd).coerceAtLeast(first.last + 1)
        // never cut an emoji in half
        if (start > 0 && line[start].isLowSurrogate()) start--
        if (end < line.length && line[end - 1].isHighSurrogate()) end++

        return SearchResultItem.Document.ExcerptData(
            text = line.substring(start, end),
            matches = matches
                .filter { it.first >= start && it.last < end }
                .map { (it.first - start)..(it.last - start) },
            heading = heading,
            truncatedStart = start > contentStart,
            truncatedEnd = end < contentEnd,
        )
    }
}

internal fun String.toSearchRegex() = toRegex(setOf(RegexOption.LITERAL, RegexOption.IGNORE_CASE))

/** the best matches first: the name before the content, the whole name before a part of it */
internal fun List<SearchResultItem>.sortedByRelevance(searchTerm: String): List<SearchResultItem> {
    val term = searchTerm.trim()
    return sortedWith(
        compareBy<SearchResultItem> {
            when {
                it.name.equals(term, ignoreCase = true) -> 0
                it.name.startsWith(term, ignoreCase = true) -> 1
                it.name.contains(term, ignoreCase = true) -> 2
                else -> 3
            }
        }
            .thenByDescending { (it as? SearchResultItem.Document)?.matchCount ?: 0 }
            .thenBy { it.name.lowercase() }
    )
}
