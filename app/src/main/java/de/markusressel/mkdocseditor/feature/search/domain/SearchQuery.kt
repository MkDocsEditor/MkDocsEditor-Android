package de.markusressel.mkdocseditor.feature.search.domain

import java.text.Normalizer

private val whitespace = Regex("\\s+")

/** Only accents. Other combining marks tell words apart, like the vowel signs of Hindi. The web client strips the same. */
private val COMBINING_ACCENTS = 0x0300..0x036F

/**
 * A search term, compared without case and accents. It is split into words, which can be found in any order.
 * The web client searches the same way.
 */
internal class SearchQuery(term: String) {

    private val words = foldForSearch(term).split(whitespace).filter { it.isNotEmpty() }.distinct()

    /** at least one word must be long enough: "a b" would match nearly everything */
    fun hasWordOf(minLength: Int): Boolean = words.any { it.length >= minLength }

    /** whether every word is in at least one of [texts], so words of a page can be in its name or in its text */
    fun matches(vararg texts: String): Boolean {
        if (words.isEmpty()) return false
        val folded = texts.map(::foldForSearch)
        return words.all { word -> folded.any { it.contains(word) } }
    }

    /**
     * whether a page is found: each word in its file name, title, description, tags or text. The text of a page that
     * asks not to be searched ([searchExclude], `search: exclude` in its front matter) is left out, as in MkDocs and the
     * web client, but the page can still be found by its name, like in the file browser.
     */
    fun matchesPage(
        name: String,
        title: String?,
        description: String?,
        tags: List<String>,
        text: String,
        searchExclude: Boolean,
    ): Boolean = matches(name, title.orEmpty(), description.orEmpty(), *tags.toTypedArray(), if (searchExclude) "" else text)

    /** every place of any word in [text], as indices of [text]; these can overlap, so [matchesIn] is for showing them */
    fun occurrencesIn(text: String): List<IntRange> {
        if (words.isEmpty()) return emptyList()
        var starts = IntArray(text.length)
        var ends = IntArray(text.length)
        val folded = foldForSearch(text) { foldedStart, foldedEnd, start, end ->
            if (foldedEnd > starts.size) {
                starts = starts.copyOf(foldedEnd * 2)
                ends = ends.copyOf(foldedEnd * 2)
            }
            for (i in foldedStart until foldedEnd) {
                starts[i] = start
                ends[i] = end
            }
            // a separate accent belongs to the letter before it
            if (foldedStart == foldedEnd && foldedStart > 0) ends[foldedStart - 1] = end
        }
        return words.flatMap { word ->
            generateSequence(folded.indexOf(word)) { folded.indexOf(word, it + word.length) }
                .takeWhile { it >= 0 }
                .map { starts[it] until ends[it + word.length - 1] }
                .toList()
        }
    }

    /** the places of the words in [text] in text order, as indices of [text], without overlaps */
    fun matchesIn(text: String): List<IntRange> = merged(occurrencesIn(text))

    /** how well the name fits: the whole name before its start, its start before all words somewhere in it */
    fun nameRank(name: String): Int {
        val folded = foldForSearch(name)
        val whole = words.joinToString(" ")
        return when {
            folded == whole -> 0
            folded.startsWith(whole) -> 1
            words.all { folded.contains(it) } -> 2
            else -> 3
        }
    }

    companion object {
        fun merged(ranges: List<IntRange>): List<IntRange> {
            val result = mutableListOf<IntRange>()
            ranges.sortedWith(compareBy({ it.first }, { it.last })).forEach { range ->
                val last = result.lastOrNull()
                if (last != null && range.first <= last.last + 1) {
                    result[result.lastIndex] = last.first..maxOf(last.last, range.last)
                } else {
                    result += range
                }
            }
            return result
        }
    }
}

private fun foldForSearch(text: String): String = foldForSearch(text) { _, _, _, _ -> }

/**
 * [text] without case and accents.
 *
 * It goes through the text piece by piece, so that [onPiece] can tell where each character came from:
 * the folded characters from `foldedStart` until `foldedEnd` are the characters of [text] from `start` until `end`.
 */
private inline fun foldForSearch(
    text: String,
    onPiece: (foldedStart: Int, foldedEnd: Int, start: Int, end: Int) -> Unit,
): String {
    val folded = StringBuilder(text.length)
    var position = 0
    while (position < text.length) {
        val codePoint = text.codePointAt(position)
        val end = position + Character.charCount(codePoint)
        val foldedStart = folded.length
        if (codePoint < 0x80) {
            // nearly all text, and this is called for every character of every page
            folded.append(codePoint.toChar().lowercaseChar())
        } else {
            val decomposed = Normalizer.normalize(text.substring(position, end), Normalizer.Form.NFD)
            decomposed.codePoints().forEach { part ->
                if (part !in COMBINING_ACCENTS) folded.appendCodePoint(part.foldedCase())
            }
        }
        onPiece(foldedStart, folded.length, position, end)
        position = end
    }
    return folded.toString()
}

/** through the upper case, because some letters have several lowercase forms, like both forms of a sigma */
private fun Int.foldedCase() = Character.toLowerCase(Character.toUpperCase(this))
