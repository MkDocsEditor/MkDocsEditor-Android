package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.feature.search.domain.SearchExcerpts
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
}
