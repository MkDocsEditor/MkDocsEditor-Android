package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.automerge.UnsyncedUploadWatcher.Result
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UnsyncedUploadWatcherTest {

    private var lastResult: Result? = null

    private fun result(block: UnsyncedUploadWatcher.() -> Unit) = runBlocking {
        val watcher = UnsyncedUploadWatcher()
        watcher.block()
        lastResult = withTimeoutOrNull(50) { watcher.await() }
    }

    @Test
    fun `confirmed changes are uploaded`() {
        result { onUnsyncedChangesChanged(false) }
        assertEquals(Result.Uploaded, lastResult)
    }

    @Test
    fun `a reader or a deleted page is refused`() {
        result { onReadOnlyChanged(true) }
        assertEquals(Result.Refused, lastResult)
        result { onDocumentDeleted() }
        assertEquals(Result.Refused, lastResult)
        result { onConnectionStatusChanged(false, CloseCode.NOT_FOUND) }
        assertEquals(Result.Refused, lastResult)
    }

    @Test
    fun `a refused session is reported`() {
        result { onConnectionStatusChanged(false, 401) }
        assertEquals(Result.SignedOut, lastResult)
        result { onConnectionStatusChanged(false, CloseCode.UNAUTHORIZED) }
        assertEquals(Result.SignedOut, lastResult)
    }

    @Test
    fun `a failed connection leaves no result, the manager tries again`() {
        result { onConnectionStatusChanged(false, null, RuntimeException("no route")) }
        assertNull(lastResult)
        result { onConnectionStatusChanged(false, CloseCode.GOING_AWAY) }
        assertNull(lastResult)
    }

    @Test
    fun `the first forbidden may be a changed role, the second is final`() {
        result { onConnectionStatusChanged(false, CloseCode.FORBIDDEN) }
        assertNull(lastResult)
        result {
            onConnectionStatusChanged(false, CloseCode.FORBIDDEN)
            onConnectionStatusChanged(false, CloseCode.FORBIDDEN)
        }
        assertEquals(Result.Refused, lastResult)
    }

    @Test
    fun `offline changes of a stored copy reach the server and complete the watcher`() {
        val dir = createTempDirectory("watcher").toFile()
        try {
            val server = FakeSyncServer("text")
            val store = FileDocumentStore(dir)
            val first = DocumentSyncSession(server.documentId, store, object : DocumentSyncSession.Listener {})
            val connection = server.connect(first)
            server.pump()
            server.disconnect(connection)
            first.onLocalTextChanged("text offline")
            first.persist()

            // what the background upload does: a new session on the stored copy
            result {
                val session = DocumentSyncSession(server.documentId, store, this)
                server.connect(session)
                server.pump()
            }
            assertEquals(Result.Uploaded, lastResult)
            assertEquals("text offline", server.document.text)
        } finally {
            dir.deleteRecursively()
        }
    }
}
