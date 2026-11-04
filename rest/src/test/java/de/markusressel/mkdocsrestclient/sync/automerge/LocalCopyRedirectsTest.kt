package de.markusressel.mkdocsrestclient.sync.automerge

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalCopyRedirectsTest {

    private val dir: File = createTempDirectory("redirects").toFile()
    private val store = FileDocumentStore(dir)

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun copy(text: String, epoch: String = "e1"): StoredDocument {
        val document = AutomergeTextDocument.load(FakeSyncServer(text).document.save(), "0a".repeat(16).hexToBytes())
        return StoredDocument(document.save(), "0a".repeat(16), epoch, emptyList())
    }

    private fun textOf(id: String) = store.load(id)?.let { AutomergeTextDocument.load(it.documentState).text }

    @Test
    fun `moved copies are re-keyed, others stay`() = runBlocking {
        store.save("moved", copy("offline edit"))
        store.save("current", copy("current"))
        store.save("gone", copy("deleted on the server"))
        val redirects = LocalCopyRedirects(store) { mapOf("moved" to "new") }

        assertEquals(mapOf("moved" to "new"), redirects.followAll())
        assertEquals(listOf("current", "gone", "new"), store.ids())
        assertEquals("offline edit", textOf("new"))
        assertNull(store.load("moved"))
    }

    @Test
    fun `opening the new id finds the copy stored under the old id`() = runBlocking {
        store.save("old", copy("offline edit"))
        val redirects = LocalCopyRedirects(store) { ids -> if ("old" in ids) mapOf("old" to "new") else emptyMap() }

        assertEquals("new", redirects.beforeOpening("new"))
        assertEquals("offline edit", textOf("new"))
    }

    @Test
    fun `opening an old id without a copy opens the new id`() = runBlocking {
        val redirects = LocalCopyRedirects(store) { ids -> if ("old" in ids) mapOf("old" to "new") else emptyMap() }
        assertEquals("new", redirects.beforeOpening("old"))
        assertEquals("other", redirects.beforeOpening("other"))
    }

    @Test
    fun `a copy of another lineage under the new id is kept as a backup`() = runBlocking {
        store.save("old", copy("moved document", epoch = "e1"))
        store.save("new", copy("stale copy of a deleted document", epoch = "e0"))
        LocalCopyRedirects(store) { ids -> if ("old" in ids) mapOf("old" to "new") else emptyMap() }.followAll()

        assertEquals(listOf("new"), store.ids())
        assertEquals("moved document", textOf("new"))
        val backup = store.backups().single()
        assertEquals("e0", backup.readText().let { Regex("\"epoch\":\"([^\"]+)\"").find(it)!!.groupValues[1] })
    }

    @Test
    fun `all copies are looked up in one request`() = runBlocking {
        store.save("a", copy("a"))
        store.save("b", copy("b"))
        val requests = mutableListOf<List<String>>()
        LocalCopyRedirects(store) { ids ->
            requests += ids
            emptyMap()
        }.followAll()

        assertEquals(listOf(listOf("a", "b")), requests)
    }

    @Test
    fun `more ids than the server accepts are split into several requests`() = runBlocking {
        val ids = List(LocalCopyRedirects.MAX_IDS_PER_LOOKUP + 1) { "id$it" }
        val fakeStore = object : LocalDocumentStore by store {
            override fun ids() = ids
        }
        val requests = mutableListOf<List<String>>()
        LocalCopyRedirects(fakeStore) { part ->
            requests += part
            emptyMap()
        }.followAll()

        assertEquals(listOf(LocalCopyRedirects.MAX_IDS_PER_LOOKUP, 1), requests.map { it.size })
        assertEquals(ids, requests.flatten())
    }

    @Test
    fun `a request without an answer keeps its copies and does not stop the others`() = runBlocking {
        val ids = List(LocalCopyRedirects.MAX_IDS_PER_LOOKUP) { "id$it" } + "old"
        val fakeStore = object : LocalDocumentStore by store {
            override fun ids() = ids
        }
        store.save("old", copy("offline edit"))
        var requests = 0
        val moved = LocalCopyRedirects(fakeStore) { part ->
            if (requests++ == 0) throw java.io.IOException("no network")
            if ("old" in part) mapOf("old" to "new") else emptyMap()
        }.followAll()

        assertEquals(2, requests)
        assertEquals(mapOf("old" to "new"), moved)
    }
}
