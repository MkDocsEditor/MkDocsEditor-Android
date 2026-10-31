package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.applySplices
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentSyncSessionTest {

    /**
     * Records the callbacks and keeps an "editor" text in sync the way the app does
     */
    private class EditorListener : DocumentSyncSession.Listener {
        var editorText: String? = null
        var peers: List<RemotePeer> = emptyList()
        var readOnly = false
        var deleted = false
        var renamedTo: String? = null
        val remoteSplices = mutableListOf<TextSplice>()

        override fun onTextReplaced(text: String) {
            editorText = text
        }

        override fun onRemoteChange(text: String, splices: List<TextSplice>) {
            // the splices must turn the shown text into the new one
            assertEquals(text, editorText!!.applySplices(splices))
            remoteSplices += splices
            editorText = text
        }

        override fun onPeersChanged(peers: List<RemotePeer>) {
            this.peers = peers
        }

        override fun onReadOnlyChanged(readOnly: Boolean) {
            this.readOnly = readOnly
        }

        override fun onDocumentDeleted() {
            deleted = true
        }

        override fun onDocumentRenamed(oldDocumentId: String, newDocumentId: String) {
            renamedTo = newDocumentId
        }
    }

    private val tempDir: File = createTempDirectory("sync-session").toFile()

    @AfterTest
    fun cleanup() {
        tempDir.deleteRecursively()
    }

    private fun DocumentSyncSession.type(newText: String, listener: EditorListener) {
        assertTrue(onLocalTextChanged(newText))
        listener.editorText = newText
        flushSync()
    }

    @Test
    fun `first connection loads the server document with a new actor id`() {
        val server = FakeSyncServer("# Hello\n")
        val store = InMemoryDocumentStore()
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, store, listener)
        assertFalse(session.hasLocalCopy)

        server.connect(session)
        server.pump()

        val hello = server.receivedHellos.single()
        assertTrue(hello.actorId.matches(Regex("[0-9a-f]{32}")))
        assertNull(hello.epoch)
        assertEquals("# Hello\n", session.text)
        assertEquals("# Hello\n", listener.editorText)
        assertEquals("epoch-1", session.epoch)

        session.persist()
        val stored = store.documents.getValue(server.documentId)
        assertEquals(hello.actorId, stored.actorId)
        assertEquals("epoch-1", stored.epoch)
    }

    @Test
    fun `edits sync between two clients through the server`() {
        val server = FakeSyncServer("one\ntwo\n")
        val alice = EditorListener()
        val bob = EditorListener()
        val aliceSession = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), alice)
        val bobSession = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), bob)
        server.connect(aliceSession, "alice")
        server.connect(bobSession, "bob")
        server.pump()

        aliceSession.type("zero\none\ntwo\n", alice)
        bobSession.type("one\ntwo\nthree\n", bob)
        server.pump()

        val expected = "zero\none\ntwo\nthree\n"
        assertEquals(expected, server.document.text)
        assertEquals(expected, aliceSession.text)
        assertEquals(expected, bobSession.text)
        assertEquals(expected, alice.editorText)
        assertEquals(expected, bob.editorText)
    }

    @Test
    fun `each client copy uses its own actor id`() {
        val server = FakeSyncServer("x")
        val a = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener())
        val b = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener())
        server.connect(a, "markus")
        server.connect(b, "markus")
        server.pump()

        val (first, second) = server.receivedHellos
        assertNotEquals(first.actorId, second.actorId)
    }

    @Test
    fun `offline edits survive a restart and merge with server changes made meanwhile`() {
        val server = FakeSyncServer("# Title\n\nSome text.\n")

        // first app process: load the document, then lose the connection
        run {
            val listener = EditorListener()
            val session = DocumentSyncSession(server.documentId, FileDocumentStore(tempDir), listener)
            val connection = server.connect(session)
            server.pump()
            server.disconnect(connection)

            // edit offline, persist (debounced in the app), then the process dies
            assertTrue(session.onLocalTextChanged("# Title\n\nSome text.\nWritten offline.\n"))
            session.persist()
        }

        // meanwhile someone else edits the document on the server
        server.edit("# New title\n\nSome text.\n")

        // second app process: a new store instance on the same files
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, FileDocumentStore(tempDir), listener)
        assertTrue(session.hasLocalCopy)
        assertEquals("# Title\n\nSome text.\nWritten offline.\n", session.text)
        listener.editorText = session.text

        server.connect(session)
        server.pump()

        val expected = "# New title\n\nSome text.\nWritten offline.\n"
        assertEquals("epoch-1", server.receivedHellos.last().epoch)
        assertEquals(expected, server.document.text)
        assertEquals(expected, session.text)
        assertEquals(expected, listener.editorText)
    }

    @Test
    fun `offline edits are rebased onto a new epoch without duplicating text`() {
        val server = FakeSyncServer("alpha\nbeta\n")
        val store = InMemoryDocumentStore()

        val first = DocumentSyncSession(server.documentId, store, EditorListener())
        val connection = server.connect(first)
        server.pump()
        server.disconnect(connection)
        first.onLocalTextChanged("alpha\nbeta\ngamma\n")
        first.persist()
        val oldActor = store.documents.getValue(server.documentId).actorId

        // the server lost its state; the file has an external edit
        server.restartWithLostState("ALPHA\nbeta\n")

        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, store, listener)
        listener.editorText = session.text
        server.connect(session)
        server.pump()

        val expected = "ALPHA\nbeta\ngamma\n"
        assertEquals(expected, session.text)
        assertEquals(expected, listener.editorText)
        assertEquals(expected, server.document.text)
        assertEquals("epoch-2", session.epoch)

        session.persist()
        val stored = store.documents.getValue(server.documentId)
        assertEquals("epoch-2", stored.epoch)
        assertNotEquals(oldActor, stored.actorId)
    }

    @Test
    fun `remote cursors are resolved and follow remote edits`() {
        val server = FakeSyncServer("hello world")
        val alice = EditorListener()
        val bob = EditorListener()
        val aliceSession = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), alice)
        val bobSession = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), bob)
        server.connect(aliceSession, "alice")
        val bobConnection = server.connect(bobSession, "bob")
        server.pump()

        // bob selects "world"
        bobSession.updateLocalSelection(anchor = 6, head = 11)
        server.pump()

        val peer = alice.peers.single()
        assertEquals("bob", peer.username)
        assertEquals("#123456", peer.color)
        assertEquals(listOf(RemoteSelection(6, 11)), peer.selections)

        // alice inserts in front of bob's selection: it moves along
        aliceSession.type(">> hello world", alice)
        server.pump()
        assertEquals(listOf(RemoteSelection(9, 14)), alice.peers.single().selections)

        server.disconnect(bobConnection)
        server.pump()
        assertTrue(alice.peers.isEmpty())
    }

    @Test
    fun `a caret at the end of the text is shared`() {
        val server = FakeSyncServer("abc")
        val alice = EditorListener()
        val aliceSession = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), alice)
        val bobSession = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener())
        server.connect(aliceSession, "alice")
        server.connect(bobSession, "bob")
        server.pump()

        bobSession.updateLocalSelection(3, 3)
        server.pump()
        assertEquals(listOf(RemoteSelection(3, 3)), alice.peers.single().selections)
    }

    @Test
    fun `a presence snapshot lists peers that joined earlier`() {
        val server = FakeSyncServer("abc")
        val first = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener())
        server.connect(first, "alice")
        server.pump()
        first.updateLocalSelection(1, 1)
        server.pump()

        val listener = EditorListener()
        server.connect(DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener), "bob")
        server.pump()

        assertEquals(listOf(RemoteSelection(1, 1)), listener.peers.single().selections)
    }

    @Test
    fun `readers cannot edit`() {
        val server = FakeSyncServer("abc", readOnly = true)
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener)
        server.connect(session)
        server.pump()

        assertTrue(listener.readOnly)
        assertFalse(session.onLocalTextChanged("changed"))
        assertEquals("abc", session.text)
    }

    @Test
    fun `rename moves the local copy and delete keeps it`() {
        val server = FakeSyncServer("abc")
        val store = InMemoryDocumentStore()
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, store, listener)
        val connection = server.connect(session)
        server.pump()

        server.sendRaw(connection, """{"type":"document-renamed","documentId":"doc-1","newDocumentId":"doc-2"}""")
        server.pump()
        assertEquals("doc-2", listener.renamedTo)
        assertEquals("doc-2", session.documentId)
        assertTrue(store.documents.containsKey("doc-2"))
        assertFalse(store.documents.containsKey("doc-1"))

        server.sendRaw(connection, """{"type":"document-deleted","documentId":"doc-2"}""")
        server.pump()
        assertTrue(listener.deleted)
        assertEquals("abc", session.text)
    }

    @Test
    fun `unknown message types are ignored`() {
        val server = FakeSyncServer("abc")
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener())
        val connection = server.connect(session)
        server.sendRaw(connection, """{"type":"something-new","foo":1}""")
        server.pump()
        assertEquals("abc", session.text)
    }
}
