package de.markusressel.mkdocsrestclient.sync.automerge

import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class UnsyncedChangesTest {

    private class Reports : DocumentSyncSession.Listener {
        val values = mutableListOf<Boolean>()
        override fun onUnsyncedChangesChanged(unsynced: Boolean) {
            values += unsynced
        }
    }

    private val tempDir: File = createTempDirectory("unsynced").toFile()
    private val store = FileDocumentStore(tempDir)

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun session(server: FakeSyncServer, listener: DocumentSyncSession.Listener = Reports()) =
        DocumentSyncSession(server.documentId, store, listener, origin = "server:1")

    @Test
    fun `a copy from the server has nothing unsynced`() {
        val server = FakeSyncServer("text")
        val session = session(server)
        server.connect(session)
        server.pump()
        session.persist()
        assertFalse(session.hasUnsyncedChanges)
        assertEquals(emptySet(), store.unsyncedIds.value)
    }

    @Test
    fun `an offline edit is unsynced until the server confirmed it`() {
        val server = FakeSyncServer("text")
        val reports = Reports()
        val session = session(server, reports)
        val connection = server.connect(session)
        server.pump()
        server.disconnect(connection)

        assertTrue(session.onLocalTextChanged("text offline"))
        session.persist()
        assertTrue(session.hasUnsyncedChanges)
        assertEquals(setOf(server.documentId), store.unsyncedIds.value)

        server.connect(session)
        assertTrue(session.hasUnsyncedChanges, "the server has not answered")
        server.pump()
        assertEquals("text offline", server.document.text)
        assertFalse(session.hasUnsyncedChanges)
        assertEquals(listOf(true, false), reports.values)

        session.persist()
        assertEquals(emptySet(), store.unsyncedIds.value)
    }

    @Test
    fun `changes of others are not unsynced`() {
        val server = FakeSyncServer("text")
        val session = session(server)
        server.connect(session)
        server.pump()
        server.edit("text of someone else")
        server.pump()
        assertEquals("text of someone else", session.text)
        assertFalse(session.hasUnsyncedChanges)
    }

    @Test
    fun `an edit of a reader never counts as synced`() {
        val server = FakeSyncServer("text")
        val session = session(server)
        val connection = server.connect(session)
        server.pump()
        server.disconnect(connection)
        assertTrue(session.onLocalTextChanged("text offline"))
        server.readOnly = true
        server.connect(session)
        server.pump()
        assertTrue(session.hasUnsyncedChanges)
    }

    @Test
    fun `the state survives a restart, also for old copies without stored heads`() {
        val server = FakeSyncServer("text")
        val session = session(server)
        val connection = server.connect(session)
        server.pump()
        server.disconnect(connection)
        assertTrue(session.onLocalTextChanged("text offline"))
        session.persist()

        val restarted = FileDocumentStore(tempDir)
        restarted.scanUnsynced()
        assertEquals(setOf(server.documentId), restarted.unsyncedIds.value)
        val stored = restarted.load(server.documentId)!!
        assertEquals("server:1", stored.origin)

        val old = StoredDocument(stored.documentState, stored.actorId, stored.epoch, stored.serverHeads)
        assertTrue(old.hasUnsyncedChanges)
    }

    @Test
    fun `a move takes the state along, keeping aside removes it`() {
        val server = FakeSyncServer("text")
        val session = session(server)
        val connection = server.connect(session)
        server.pump()
        server.disconnect(connection)
        assertTrue(session.onLocalTextChanged("text offline"))
        session.persist()

        store.move(server.documentId, "moved")
        assertEquals(setOf("moved"), store.unsyncedIds.value)

        store.keepAside("moved")
        assertEquals(emptySet(), store.unsyncedIds.value)
    }

    @Test
    fun `unsynced edits never reach another server with the same document id`() {
        val first = FakeSyncServer("first")
        val firstSession = session(first)
        val connection = first.connect(firstSession)
        first.pump()
        first.disconnect(connection)
        assertTrue(firstSession.onLocalTextChanged("first offline"))
        firstSession.persist()

        val second = FakeSyncServer("second", documentId = first.documentId)
        second.restartWithLostState("second")
        val session = DocumentSyncSession(second.documentId, store, Reports(), origin = "server:2")
        second.connect(session)
        second.pump()

        assertEquals("second", session.text)
        assertEquals("second", second.document.text)

        session.persist()
        assertEquals("server:2", store.load(second.documentId)?.origin)
        assertEquals(1, store.backups().size, "the edits of the first server stay available")
    }

    @Test
    fun `a pinned store stays with its server, and unsynced edits are back after switching back`() {
        val first = FakeSyncServer("first")
        val firstSession = session(first)
        val connection = first.connect(firstSession)
        first.pump()
        first.disconnect(connection)
        assertTrue(firstSession.onLocalTextChanged("first offline"))
        firstSession.persist()

        val otherDirectory = createTempDirectory("unsynced-other").toFile()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        try {
            val other = FileDocumentStore(otherDirectory)
            val scoped = ScopedDocumentStore(store, scope)
            val pinned = scoped.pinned()
            assertEquals(setOf(first.documentId), scoped.unsyncedIds.value)

            scoped.switchTo(other)
            assertEquals(emptySet(), scoped.unsyncedIds.value)
            assertNull(scoped.load(first.documentId))
            assertEquals(setOf(first.documentId), pinned.ids().toSet())

            scoped.switchTo(store)
            assertEquals(setOf(first.documentId), scoped.unsyncedIds.value)
            assertEquals("first offline", DocumentSyncSession(first.documentId, scoped, Reports(), origin = "server:1").text)
        } finally {
            scope.cancel()
            otherDirectory.deleteRecursively()
        }
    }
}
