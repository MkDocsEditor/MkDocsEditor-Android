package de.markusressel.mkdocsrestclient.sync.automerge

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDocumentStoreTest {

    @Test
    fun `documents survive a new store instance and can be moved`() {
        val dir = createTempDirectory("store").toFile()
        try {
            FileDocumentStore(dir).save(
                "a/b c",
                StoredDocument(byteArrayOf(1, 2, 3), "00ff", "e1", listOf("abcd")),
            )

            val store = FileDocumentStore(dir)
            val loaded = requireNotNull(store.load("a/b c"))
            assertEquals(listOf<Byte>(1, 2, 3), loaded.documentState.toList())
            assertEquals("00ff", loaded.actorId)
            assertEquals("e1", loaded.epoch)
            assertEquals(listOf("abcd"), loaded.serverHeads)

            store.move("a/b c", "new-id")
            assertNull(store.load("a/b c"))
            assertEquals("e1", store.load("new-id")?.epoch)
            assertNull(store.load("missing"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `moving onto a copy of the same lineage merges both`() {
        val dir = createTempDirectory("store").toFile()
        try {
            val store = FileDocumentStore(dir)
            val base = FakeSyncServer("base").document.save()
            val actor = "0b".repeat(16)
            // two copies that share the start and each have their own edit
            val a = AutomergeTextDocument.load(base, actor.hexToBytes()).apply { applyLocalText("base A") }
            val b = AutomergeTextDocument.load(base, "0c".repeat(16).hexToBytes()).apply { applyLocalText("B base") }
            store.save("old", StoredDocument(a.save(), actor, "e1", listOf("aa")))
            store.save("new", StoredDocument(b.save(), "0c".repeat(16), "e1", listOf("bb")))

            store.move("old", "new")

            assertEquals(listOf("new"), store.ids())
            val merged = requireNotNull(store.load("new"))
            assertEquals("B base A", AutomergeTextDocument.load(merged.documentState).text)
            assertEquals("0c".repeat(16), merged.actorId)
            assertEquals(listOf("bb"), merged.serverHeads)
            assertTrue(store.backups().isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `a copy that cannot be merged is kept as a backup`() {
        val dir = createTempDirectory("store").toFile()
        try {
            val store = FileDocumentStore(dir)
            val good = FakeSyncServer("good").document.save()
            store.save("old", StoredDocument(byteArrayOf(1, 2, 3), "00ff", "e1", emptyList()))
            store.save("new", StoredDocument(good, "0d".repeat(16), "e1", emptyList()))

            store.move("old", "new")

            assertEquals(listOf("new"), store.ids())
            assertEquals("good", AutomergeTextDocument.load(store.load("new")!!.documentState).text)
            assertEquals(1, store.backups().size)
        } finally {
            dir.deleteRecursively()
        }
    }
}
