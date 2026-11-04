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
        store.save("offline", copy("no answer"))
        val redirects = LocalCopyRedirects(store) { id ->
            when (id) {
                "moved" -> LocalCopyRedirects.Lookup.Moved("new")
                "offline" -> throw java.io.IOException("no network")
                "gone" -> LocalCopyRedirects.Lookup.NotMoved
                else -> LocalCopyRedirects.Lookup.NotMoved
            }
        }

        assertEquals(mapOf("moved" to "new"), redirects.followAll())
        assertEquals(listOf("current", "gone", "new", "offline"), store.ids())
        assertEquals("offline edit", textOf("new"))
        assertNull(store.load("moved"))
    }

    @Test
    fun `opening the new id finds the copy stored under the old id`() = runBlocking {
        store.save("old", copy("offline edit"))
        val redirects = LocalCopyRedirects(store) { id ->
            if (id == "old") LocalCopyRedirects.Lookup.Moved("new") else LocalCopyRedirects.Lookup.NotMoved
        }

        assertEquals("new", redirects.beforeOpening("new"))
        assertEquals("offline edit", textOf("new"))
    }

    @Test
    fun `opening an old id without a copy opens the new id`() = runBlocking {
        val redirects = LocalCopyRedirects(store) { id ->
            if (id == "old") LocalCopyRedirects.Lookup.Moved("new") else LocalCopyRedirects.Lookup.NotMoved
        }
        assertEquals("new", redirects.beforeOpening("old"))
        assertEquals("other", redirects.beforeOpening("other"))
    }

    @Test
    fun `a copy of another lineage under the new id is kept as a backup`() = runBlocking {
        store.save("old", copy("moved document", epoch = "e1"))
        store.save("new", copy("stale copy of a deleted document", epoch = "e0"))
        LocalCopyRedirects(store) { id ->
            if (id == "old") LocalCopyRedirects.Lookup.Moved("new") else LocalCopyRedirects.Lookup.NotMoved
        }.followAll()

        assertEquals(listOf("new"), store.ids())
        assertEquals("moved document", textOf("new"))
        val backup = store.backups().single()
        assertEquals("e0", backup.readText().let { Regex("\"epoch\":\"([^\"]+)\"").find(it)!!.groupValues[1] })
    }
}
