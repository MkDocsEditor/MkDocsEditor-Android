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
        val query = SearchQuery(searchTerm)
        // only when a line matches: finding the headings is the expensive part
        val headings by lazy(LazyThreadSafetyMode.NONE) { Headings.of(content) }

        var matchCount = 0
        val excerpts = content.split('\n').mapIndexedNotNull { index, line ->
            val occurrences = query.occurrencesIn(line)
            if (occurrences.isEmpty()) return@mapIndexedNotNull null
            matchCount += occurrences.size

            val lineNumber = index + 1
            excerpt(
                line = line,
                matches = SearchQuery.merged(occurrences),
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

/**
 * the best matches first: the name before the content, the whole name before a part of it. A page's title and its file
 * name both count as its name.
 */
internal fun List<SearchResultItem>.sortedByRelevance(searchTerm: String): List<SearchResultItem> {
    val query = SearchQuery(searchTerm)
    return sortedWith(
        compareBy<SearchResultItem> { item ->
            listOfNotNull(item.name, (item as? SearchResultItem.Document)?.fileName).minOf(query::nameRank)
        }
            .thenByDescending { (it as? SearchResultItem.Document)?.matchCount ?: 0 }
            .thenBy { it.name.lowercase() }
    )
}
