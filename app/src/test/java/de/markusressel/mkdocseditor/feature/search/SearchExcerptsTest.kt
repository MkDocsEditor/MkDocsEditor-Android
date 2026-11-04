package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.feature.search.domain.SearchExcerpts
import de.markusressel.mkdocseditor.feature.search.domain.SearchQuery
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.sortedByRelevance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchExcerptsTest {

    @Test
    fun `an excerpt names the heading it is below`() {
        val result = SearchExcerpts.find("# Top\n\n## Kosten\n\nDie Haftpflicht kostet 50 €\n", "haftpflicht")
        val excerpt = result.excerpts.single()
        assertEquals("Kosten", excerpt.heading)
        assertEquals("Die Haftpflicht kostet 50 €", excerpt.text)
        assertEquals("Haftpflicht", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `several matches in a line are one excerpt but all count`() {
        val result = SearchExcerpts.find("ab ab\nnothing\nab", "ab")
        assertEquals(3, result.matchCount)
        assertEquals(2, result.excerpts.size)
        assertEquals(listOf(0..1, 3..4), result.excerpts.first().matches)
    }

    @Test
    fun `a long line is cut around the match`() {
        val line = "x".repeat(100) + "needle" + "y".repeat(200)
        val excerpt = SearchExcerpts.find(line, "needle").excerpts.single()
        assertTrue(excerpt.truncatedStart)
        assertTrue(excerpt.truncatedEnd)
        assertTrue(excerpt.text.length < 100, "${excerpt.text.length} characters")
        assertEquals("needle", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `line markers are not part of the excerpt`() {
        listOf("- needle", "> needle", "1. needle", "## needle").forEach { line ->
            val excerpt = SearchExcerpts.find(line, "needle").excerpts.single()
            assertEquals("needle", excerpt.text)
            assertFalse(excerpt.truncatedStart, line)
        }
    }

    @Test
    fun `a matching heading is not its own context`() {
        val result = SearchExcerpts.find("# First\n\n# Needle\n", "needle")
        assertNull(result.excerpts.single().heading)
    }

    @Test
    fun `a match inside a code block has no heading from the code`() {
        val result = SearchExcerpts.find("# Real\n\n```\n# not a heading\nneedle\n```\n", "needle")
        assertEquals("Real", result.excerpts.single().heading)
    }

    @Test
    fun `a search term with regex characters is literal`() {
        assertEquals(1, SearchExcerpts.find("a.b axb", "a.b").matchCount)
    }

    @Test
    fun `an emoji is not cut in half`() {
        val line = "😀".repeat(30) + "needle"
        val excerpt = SearchExcerpts.find(line, "needle").excerpts.single()
        assertFalse(excerpt.text.first().isLowSurrogate())
        assertEquals("needle", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `names beat content, then the number of matches decides`() {
        val results = listOf(
            SearchResultItem.Document("few", "few", matchCount = 1),
            SearchResultItem.Document("many", "many", matchCount = 9),
            SearchResultItem.Document("part", "a needle page"),
            SearchResultItem.Resource("start", "needle.png"),
            SearchResultItem.Section("exact", "Needle"),
        ).sortedByRelevance("needle").map { it.name }
        assertEquals(listOf("Needle", "needle.png", "a needle page", "many", "few"), results)
    }

    @Test
    fun `a term without accents finds text with them, and the highlight covers the original`() {
        val excerpt = SearchExcerpts.find("Der Kürbis ist reif", "KURBIS").excerpts.single()
        assertEquals("Kürbis", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `a term with accents finds text without them`() {
        assertEquals(1, SearchExcerpts.find("der Kurbis", "kürbis").matchCount)
    }

    @Test
    fun `a separate accent is part of the highlight, also after the end of the term`() {
        val text = "Kürbis̈ x"
        val excerpt = SearchExcerpts.find(text, "kurbis").excerpts.single()
        assertEquals("Kürbis̈", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `a precomposed accent lines up the highlights behind it`() {
        val excerpt = SearchExcerpts.find("äää needle", "needle").excerpts.single()
        assertEquals("needle", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `a letter that folds into several characters lines up the highlights behind it`() {
        // Hangul syllables decompose into several letters without combining marks
        val excerpt = SearchExcerpts.find("한글 needle", "needle").excerpts.single()
        assertEquals("needle", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `an emoji before a match lines up the highlight`() {
        val excerpt = SearchExcerpts.find("😀 Kürbis", "kurbis").excerpts.single()
        assertEquals("Kürbis", excerpt.text.substring(excerpt.matches.single()))
    }

    @Test
    fun `every word of a term is highlighted, in the order of the text`() {
        val excerpt = SearchExcerpts.find("Kuchen mit Apfel", "apfel kuchen").excerpts.single()
        assertEquals(listOf("Kuchen", "Apfel"), excerpt.matches.map { excerpt.text.substring(it) })
    }

    @Test
    fun `overlapping words are one highlight but both count`() {
        val result = SearchExcerpts.find("Apfelkuchen", "apfelk kuchen")
        val excerpt = result.excerpts.single()
        assertEquals(listOf(0..10), excerpt.matches)
        assertEquals(2, result.matchCount)
    }

    @Test
    fun `a term of only spaces finds nothing`() {
        assertEquals(0, SearchExcerpts.find("a b", "  ").matchCount)
        assertFalse(SearchQuery("  ").matches("a b"))
    }

    @Test
    fun `an empty term finds nothing`() {
        assertFalse(SearchQuery("").matches("a"))
    }

    @Test
    fun `words can be in any order and far apart`() {
        assertTrue(SearchQuery("apfel kuchen").matches("Kuchen\n\nmit viel Apfel"))
    }

    @Test
    fun `a page needs every word, but each one in the name or in the text`() {
        val query = SearchQuery("apfel kuchen")
        assertTrue(query.matches("Apfel", "ein Kuchen"))
        assertFalse(query.matches("Apfel", "eine Torte"))
    }

    @Test
    fun `the forms of a sigma are the same letter`() {
        assertTrue(SearchQuery("ΟΔΟΣ").matches("οδος"))
        assertTrue(SearchQuery("οδος").matches("ΟΔΟΣ"))
    }

    @Test
    fun `the name decides with accents and words too`() {
        val results = listOf(
            SearchResultItem.Document("text", "Torte", matchCount = 9),
            SearchResultItem.Document("words", "Kuchen mit Äpfeln"),
            SearchResultItem.Document("start", "Äpfel kuchen und mehr"),
            SearchResultItem.Document("exact", "Äpfel Kuchen"),
        ).sortedByRelevance("apfel kuchen").map { it.documentIdOf() }
        assertEquals(listOf("exact", "start", "words", "text"), results)
    }

    @Test
    fun `a vowel sign is not an accent, so it tells words apart`() {
        // "kaam" and "kam" in Hindi; both are the same without the vowel sign U+093E
        assertFalse(SearchQuery("काम").matches("कम"))
    }

    @Test
    fun `letters that are one letter in upper case are the same`() {
        assertTrue(SearchQuery("kapi").matches("KAPı"))
        assertTrue(SearchQuery("mass").matches("maſs"))
    }

    @Test
    fun `a word typed twice counts once`() {
        assertEquals(1, SearchExcerpts.find("ein Apfel", "apfel apfel").matchCount)
    }

    @Test
    fun `only a word of enough letters makes a term searchable`() {
        assertFalse(SearchQuery("a b").hasWordOf(2))
        assertTrue(SearchQuery("a bc").hasWordOf(2))
    }

    private fun SearchResultItem.documentIdOf() = (this as SearchResultItem.Document).documentId
}
