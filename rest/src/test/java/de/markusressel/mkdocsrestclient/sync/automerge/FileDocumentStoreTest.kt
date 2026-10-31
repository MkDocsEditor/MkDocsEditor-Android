package de.markusressel.mkdocsrestclient.sync.automerge

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
