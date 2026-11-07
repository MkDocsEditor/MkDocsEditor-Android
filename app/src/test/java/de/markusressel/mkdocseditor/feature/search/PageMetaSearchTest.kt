package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.feature.search.domain.SearchQuery
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.domain.sortedByRelevance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageMetaSearchTest {

    private fun SearchQuery.finds(
        name: String = "setup",
        title: String? = null,
        description: String? = null,
        tags: List<String> = emptyList(),
        text: String = "",
        searchExclude: Boolean = false,
    ) = matchesPage(name, title, description, tags, text, searchExclude)

    @Test
    fun `a page is found by its title, description and tags`() {
        assertTrue(SearchQuery("getting started").finds(title = "Getting started"))
        assertTrue(SearchQuery("first steps").finds(description = "First steps"))
        assertTrue(SearchQuery("intro").finds(tags = listOf("setup", "intro")))
    }

    @Test
    fun `the words of a search can be in different fields`() {
        assertTrue(SearchQuery("getting intro").finds(title = "Getting started", tags = listOf("intro")))
        assertFalse(SearchQuery("getting garden").finds(title = "Getting started", tags = listOf("intro")))
    }

    @Test
    fun `a page without page metadata is found by its name and text as before`() {
        assertTrue(SearchQuery("setup").finds())
        assertTrue(SearchQuery("router").finds(text = "The router is in the hall"))
        assertFalse(SearchQuery("intro").finds())
    }

    @Test
    fun `a page that asks not to be searched is never found`() {
        assertFalse(SearchQuery("setup").finds(searchExclude = true))
        assertFalse(SearchQuery("getting").finds(title = "Getting started", searchExclude = true))
        assertFalse(SearchQuery("router").finds(text = "The router", searchExclude = true))
    }

    @Test
    fun `the title and the file name both rank as the name`() {
        val byText = SearchResultItem.Document("d1", "Notes", matchCount = 5)
        val byFileName = SearchResultItem.Document("d2", "Getting started", fileName = "router")
        val byTitle = SearchResultItem.Document("d3", "Router")

        val ranked = listOf(byText, byFileName, byTitle).sortedByRelevance("router").map { it.name }

        // both names fit as a whole, so they come before the match in the text, and then by name
        assertEquals(listOf("Getting started", "Router", "Notes"), ranked)
    }
}
