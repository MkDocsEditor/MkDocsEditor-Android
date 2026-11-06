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
        var lastPositions = emptyMap<String, RemotePosition>()
        var ghosts = emptyList<RemoteGhost>()
        var presenceKnown = false
        var editorText: String? = null
        var peers: List<RemotePeer> = emptyList()
        var readOnly = false
        var deleted = false
        var renamedTo: String? = null
        val remoteSplices = mutableListOf<TextSplice>()
        var serverAnswers = 0

        override fun onServerAnswered() {
            serverAnswers++
        }

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

        override fun onLastPositionsChanged(positions: Map<String, RemotePosition>) {
            lastPositions = positions
        }

        override fun onGhostsChanged(ghosts: List<RemoteGhost>) {
            this.ghosts = ghosts
        }

        override fun onPresenceKnownChanged(known: Boolean) {
            presenceKnown = known
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
    fun `the document and each sync message of the server count as its answer`() {
        val server = FakeSyncServer("one\n")
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener)
        assertEquals(0, listener.serverAnswers)

        server.connect(session)
        server.pump()
        assertTrue(listener.serverAnswers > 0)

        val before = listener.serverAnswers
        session.type("one\ntwo\n", listener)
        server.pump()
        assertTrue(listener.serverAnswers > before, "the answer to an edit")
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
    fun `last positions of a snapshot are resolved and unknown cursors left out`() {
        val server = FakeSyncServer("abc")
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener)
        server.connect(session)
        server.pump()

        session.onMessage(
            """{"type":"presence-snapshot","documentId":"${server.documentId}","peers":[],"lastPositions":[
                {"accountId":"gone","selections":[{"anchor":"end","head":"end"}]},
                {"accountId":"deleted-text","selections":[{"anchor":"nonsense","head":"nonsense"}]},
                {"accountId":"empty","selections":[]},
                {"accountId":"reader","selections":[],"viewport":{"top":"start","bottom":"end"}}
            ]}"""
        )

        assertEquals(
            mapOf(
                "gone" to RemotePosition(RemoteSelection(3, 3), null),
                "reader" to RemotePosition(null, RemoteViewport(0, 3)),
            ),
            listener.lastPositions,
        )
    }

    private fun connectedSession(listener: EditorListener, now: () -> Long = { 0 }): Pair<FakeSyncServer, DocumentSyncSession> {
        val server = FakeSyncServer("abc")
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener, now = now)
        server.connect(session)
        server.pump()
        return server to session
    }

    @Test
    fun `an instance that leaves stays as a ghost with its name and device`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)
        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","accountId":"a1","username":"bob",
                "selections":[{"anchor":"end","head":"end"}]}"""
        )

        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","accountId":"a1","deviceName":"Pixel","instanceColor":"#112233",
                "selections":[{"anchor":"end","head":"end"}],"viewport":{"top":"start","bottom":"end"},"ghostMs":60000}"""
        )

        assertTrue(listener.peers.isEmpty())
        val ghost = listener.ghosts.single()
        assertEquals("i1", ghost.instanceId)
        assertEquals("a1", ghost.accountId)
        assertEquals("bob", ghost.username)
        assertEquals("Pixel", ghost.deviceName)
        assertEquals("#112233", ghost.instanceColor)
        assertEquals(listOf(RemoteSelection(3, 3)), ghost.selections)
        assertEquals(RemoteViewport(0, 3), ghost.viewport)
        assertEquals(60000, ghost.remainingMs)
    }

    @Test
    fun `a leave without a position leaves no ghost`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)
        session.onMessage("""{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1"}""")

        assertTrue(listener.ghosts.isEmpty())
    }

    @Test
    fun `several instances of one account each have a ghost`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)
        listOf("i1", "i2").forEach {
            session.onMessage(
                """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c-$it","instanceId":"$it","accountId":"a1","ghostMs":60000}"""
            )
        }

        assertEquals(listOf("i1", "i2"), listener.ghosts.map { it.instanceId })
    }

    @Test
    fun `a ghost ends when its instance is back, not with another instance of the account`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)
        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","accountId":"a1","ghostMs":60000}"""
        )
        assertEquals(1, listener.ghosts.size)

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c2","instanceId":"i2","accountId":"a1","username":"bob","selections":[]}"""
        )
        assertEquals(1, listener.ghosts.size)

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c3","instanceId":"i1","accountId":"a1","username":"bob","selections":[]}"""
        )
        assertTrue(listener.ghosts.isEmpty())
    }

    @Test
    fun `the ghosts of a snapshot have their time left`() {
        var time = 1000L
        val listener = EditorListener()
        val (server, session) = connectedSession(listener) { time }

        session.onMessage(
            """{"type":"presence-snapshot","documentId":"${server.documentId}",
                "peers":[{"documentId":"${server.documentId}","clientId":"c1","instanceId":"i9","accountId":"a1","username":"o","selections":[]}],
                "lastPositions":[{"accountId":"long-gone","selections":[{"anchor":"end","head":"end"}]}],
                "ghosts":[{"instanceId":"i1","accountId":"a1","username":"bob","displayName":"Bob","color":"#112233","instanceColor":"#445566","deviceName":"Pixel",
                    "selections":[],"viewport":{"top":"start","bottom":"end"},"ghostMs":20000}]}"""
        )

        val ghost = listener.ghosts.single()
        assertEquals("i1", ghost.instanceId)
        assertEquals("a1", ghost.accountId)
        assertEquals("Bob", ghost.displayName)
        assertEquals("#445566", ghost.instanceColor)
        assertEquals("Pixel", ghost.deviceName)
        assertEquals(20000, ghost.remainingMs)
        assertTrue(listener.presenceKnown)

        // a later change must not bring back the time, or an expired ghost
        time += 5000
        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","instanceId":"i9","accountId":"a1","ghostMs":100}"""
        )
        assertEquals(15000, listener.ghosts.first { it.instanceId == "i1" }.remainingMs)
        time += 20000
        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c2","instanceId":"i2","accountId":"other","ghostMs":100}"""
        )
        assertEquals(listOf("i2"), listener.ghosts.map { it.instanceId })
    }

    @Test
    fun `a server without instances makes each connection an instance`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c1","accountId":"a1","username":"bob","selections":[]}"""
        )
        assertEquals("c1", listener.peers.single().instanceId)

        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","accountId":"a1","ghostMs":60000}"""
        )
        assertEquals("c1", listener.ghosts.single().instanceId)
    }

    @Test
    fun `the peers carry their instance, device and color`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","deviceName":"Pixel","instanceColor":"#112233",
                "color":"#445566","accountId":"a1","username":"bob","selections":[]}"""
        )

        val peer = listener.peers.single()
        assertEquals("i1", peer.instanceId)
        assertEquals("Pixel", peer.deviceName)
        assertEquals("#112233", peer.instanceColor)
        assertEquals("#445566", peer.color)
    }

    @Test
    fun `peers and ghosts carry the profile picture, a leave keeps the one of the presence`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","accountId":"a1","username":"bob",
                "avatar":"0123456789abcdef","selections":[]}"""
        )
        assertEquals("0123456789abcdef", listener.peers.single().avatar)

        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","accountId":"a1",
                "selections":[{"anchor":"end","head":"end"}],"ghostMs":60000}"""
        )
        assertEquals("0123456789abcdef", listener.ghosts.single().avatar)

        session.onMessage(
            """{"type":"presence-snapshot","documentId":"${server.documentId}",
                "ghosts":[{"instanceId":"i2","accountId":"a2","username":"anna","avatar":"fedcba9876543210","selections":[],"ghostMs":20000}]}"""
        )
        assertEquals("fedcba9876543210", listener.ghosts.single { it.instanceId == "i2" }.avatar)
    }

    @Test
    fun `hello names the instance and its color, the same after a reconnect`() {
        val session = DocumentSyncSession("doc", InMemoryDocumentStore(), EditorListener(), instanceId = "0f".repeat(16))
        session.updateLocalColor("#112233")
        val sent = mutableListOf<String>()

        session.onConnected(send = { sent += it })
        session.onDisconnected()
        session.onConnected(send = { sent += it })

        assertEquals(2, sent.size)
        sent.forEach {
            assertTrue(it.contains("\"instanceId\":\"${"0f".repeat(16)}\""), it)
            assertTrue(it.contains("\"instanceColor\":\"#112233\""), it)
        }
    }

    @Test
    fun `an old connection of this instance is no peer`() {
        val listener = EditorListener()
        val server = FakeSyncServer("abc")
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener, instanceId = "mine")
        server.connect(session)
        server.pump()

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c1","instanceId":"mine","accountId":"a1","username":"me","selections":[]}"""
        )
        assertTrue(listener.peers.isEmpty())

        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"c2","instanceId":"other","accountId":"a1","username":"me","selections":[]}"""
        )
        assertEquals(listOf("other"), listener.peers.map { it.instanceId })
    }

    @Test
    fun `the id the server uses goes before the one asked for`() {
        val server = FakeSyncServer("abc")
        server.instanceIdInUse = "c-7"
        val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener(), instanceId = "mine")
        assertEquals("mine", session.instanceIdInUse)

        server.connect(session)
        server.pump()

        assertEquals("c-7", session.instanceIdInUse)
    }

    @Test
    fun `a new instance id is what the server accepts`() {
        val first = newInstanceId()
        assertTrue(first.matches(Regex("^[0-9a-f]{32}$")), first)
        assertNotEquals(first, newInstanceId())
    }

    @Test
    fun `a color is sent, and clearing it is an explicit null, because a missing one keeps the last`() {
        val (server, session) = connectedSession(EditorListener())

        session.updateLocalColor("#112233")
        server.pump()
        val set = server.receivedPresences.last()
        assertTrue(set.contains("\"instanceColor\":\"#112233\""), set)

        session.updateLocalColor(null)
        server.pump()
        val cleared = server.receivedPresences.last()
        assertTrue(cleared.contains("\"instanceColor\":null"), cleared)
    }

    @Test
    fun `presence is not known while disconnected`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)
        assertTrue(listener.presenceKnown)
        session.onMessage(
            """{"type":"presence-leave","documentId":"${server.documentId}","clientId":"c1","instanceId":"i1","accountId":"a1","ghostMs":60000}"""
        )

        session.onDisconnected()

        assertFalse(listener.presenceKnown)
        assertTrue(listener.ghosts.isEmpty())
    }

    @Test
    fun `the viewport reaches other users`() {
        val server = FakeSyncServer("abc")
        val aliceListener = EditorListener()
        server.connect(DocumentSyncSession(server.documentId, InMemoryDocumentStore(), aliceListener), "alice")
        val bob = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), EditorListener())
        server.connect(bob, "bob")
        server.pump()

        bob.updateLocalViewport(1, 2)
        server.pump()

        assertEquals(RemoteViewport(1, 2), aliceListener.peers.single().viewport)
    }

    @Test
    fun `a viewport that goes away is sent as an explicit null, because a missing one keeps the last`() {
        val listener = EditorListener()
        val (server, session) = connectedSession(listener)

        session.updateLocalViewport(null, null)
        server.pump()

        val sent = server.receivedPresences.last()
        assertTrue(sent.contains("\"viewport\":null"), sent)
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
    fun `offline edits follow a move made while the app was offline`() {
        val server = FakeSyncServer("# Title\n\nSome text.\n")

        // first app process: load the document, go offline, edit, persist, die
        run {
            val listener = EditorListener()
            val session = DocumentSyncSession(server.documentId, FileDocumentStore(tempDir), listener)
            val connection = server.connect(session)
            server.pump()
            server.disconnect(connection)
            assertTrue(session.onLocalTextChanged("# Title\n\nSome text.\nWritten offline.\n"))
            session.persist()
        }

        // meanwhile the document moves on the server, and the move rewrites a link in it
        server.move("doc-2")
        server.edit("# Title\n\nSome text, rewritten by the move.\n")

        // second app process: connects with the old id, learns the new one right after hello
        val store = FileDocumentStore(tempDir)
        val listener = EditorListener()
        val session = DocumentSyncSession("doc-1", store, listener)
        listener.editorText = session.text
        server.connect(session)
        server.pump()
        assertEquals("doc-2", listener.renamedTo)
        assertEquals("doc-2", session.documentId)
        assertEquals(listOf("doc-2"), store.ids())
        assertEquals("# Title\n\nSome text.\nWritten offline.\n", store.load("doc-2")?.let {
            AutomergeTextDocument.load(it.documentState).text
        })

        // the app reconnects with the new id and the same epoch: a normal sync merges
        server.connect(session)
        server.pump()
        val expected = "# Title\n\nSome text, rewritten by the move.\nWritten offline.\n"
        assertEquals("epoch-1", server.receivedHellos.last().epoch)
        assertEquals("doc-2", server.receivedHellos.last().documentId)
        assertEquals(expected, server.document.text)
        assertEquals(expected, session.text)
        assertEquals(expected, listener.editorText)
    }

    @Test
    fun `unsaved edits are kept when the document moves while connected`() {
        val server = FakeSyncServer("abc")
        val store = FileDocumentStore(tempDir)
        val listener = EditorListener()
        val session = DocumentSyncSession(server.documentId, store, listener)
        server.connect(session)
        server.pump()
        listener.editorText = session.text

        // typed, but neither sent nor persisted yet
        assertTrue(session.onLocalTextChanged("abc typed"))
        server.move("doc-2")
        server.pump()

        assertEquals("doc-2", session.documentId)
        assertEquals(listOf("doc-2"), store.ids())
        assertEquals("abc typed", AutomergeTextDocument.load(store.load("doc-2")!!.documentState).text)

        server.connect(session)
        server.pump()
        session.flushSync()
        server.pump()
        assertEquals("abc typed", server.document.text)
    }

    @Test
    fun `a move merges with an older copy stored under the new id`() {
        val server = FakeSyncServer("abc")
        val store = FileDocumentStore(tempDir)
        // an interrupted earlier move left a copy under the new id
        run {
            val session = DocumentSyncSession(server.documentId, store, EditorListener())
            val connection = server.connect(session)
            server.pump()
            server.disconnect(connection)
            assertTrue(session.onLocalTextChanged("abc 1"))
            session.persist()
        }
        store.save("doc-2", store.load("doc-1")!!)

        val listener = EditorListener()
        val session = DocumentSyncSession("doc-1", store, listener)
        listener.editorText = session.text
        assertTrue(session.onLocalTextChanged("abc 1 2"))
        listener.editorText = "abc 1 2"
        server.move("doc-2")
        server.connect(session)
        server.pump()

        assertEquals(listOf("doc-2"), store.ids())
        assertEquals("abc 1 2", session.text)
        assertEquals("abc 1 2", listener.editorText)
    }

    @Test
    fun `persisting never overwrites a copy re-keyed onto the open document`() {
        val server = FakeSyncServer("abc")
        val store = FileDocumentStore(tempDir)
        // a copy of the same document with an offline edit, still under another id
        run {
            val session = DocumentSyncSession(server.documentId, store, EditorListener())
            val connection = server.connect(session)
            server.pump()
            server.disconnect(connection)
            assertTrue(session.onLocalTextChanged("offline abc"))
            session.persist()
        }
        store.move("doc-1", "elsewhere")
        store.save("doc-1", store.load("elsewhere")!!.let { copy ->
            // the older state, as the open editor loaded it
            val doc = AutomergeTextDocument.load(server.document.save(), copy.actorId.hexToBytes())
            StoredDocument(doc.save(), "0e".repeat(16), copy.epoch, copy.serverHeads)
        })

        // an editor has doc-1 open while a redirect re-keys the other copy onto doc-1
        val listener = EditorListener()
        val session = DocumentSyncSession("doc-1", store, listener)
        listener.editorText = session.text
        assertTrue(session.onLocalTextChanged("abc typed"))
        listener.editorText = "abc typed"
        store.move("elsewhere", "doc-1")
        session.persist()

        val text = AutomergeTextDocument.load(store.load("doc-1")!!.documentState).text
        assertEquals("offline abc typed", text)
        assertEquals(text, session.text)
        assertEquals(text, listener.editorText)
        assertTrue(store.backups().isEmpty())
    }

    @Test
    fun `persisting keeps a stored copy of another lineage as a backup`() {
        val server = FakeSyncServer("abc")
        val store = InMemoryDocumentStore()
        val session = DocumentSyncSession(server.documentId, store, EditorListener())
        server.connect(session)
        server.pump()
        session.persist()

        val foreign = StoredDocument(FakeSyncServer("other").document.save(), "0f".repeat(16), "epoch-9", emptyList())
        store.save("doc-1", foreign)
        assertTrue(session.onLocalTextChanged("abc typed"))
        session.persist()

        assertEquals(listOf(foreign), store.backups)
        assertEquals("abc typed", AutomergeTextDocument.load(store.load("doc-1")!!.documentState).text)
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
