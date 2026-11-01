package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.feature.search.data.RecentSearchStore
import kotlin.test.Test
import kotlin.test.assertEquals

class RecentSearchStoreTest {

    private val store = RecentSearchStore(InMemoryKeyValues())

    @Test
    fun `the last used term comes first and is not listed twice`() {
        store.remember("first")
        store.remember("second")
        store.remember("first")
        assertEquals(listOf("first", "second"), store.load())
    }

    @Test
    fun `terms that only differ in case are the same term, the latest spelling stays`() {
        store.remember("haft")
        store.remember("Haft")
        assertEquals(listOf("Haft"), store.load())
    }

    @Test
    fun `whitespace does not make a new term`() {
        store.remember("  zwei   worte ")
        store.remember("zwei\nworte")
        assertEquals(listOf("zwei worte"), store.load())
    }

    @Test
    fun `only the latest ten terms stay`() {
        (1..12).forEach { store.remember("term$it") }
        assertEquals(10, store.load().size)
        assertEquals("term12", store.load().first())
        assertEquals("term3", store.load().last())
    }
}
