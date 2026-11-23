package de.markusressel.mkdocsrestclient.sync.automerge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The focus of an AI agent comes as indexes at a revision, see docs/agent-presence.md of the backend */
class AgentFocusTest {

    private var time = 0L
    private val peers = mutableListOf<List<RemotePeer>>()
    private val listener = object : DocumentSyncSession.Listener {
        override fun onPeersChanged(peers: List<RemotePeer>) {
            this@AgentFocusTest.peers += peers
        }
    }

    private lateinit var server: FakeSyncServer
    private lateinit var session: DocumentSyncSession

    private fun connect(text: String) {
        server = FakeSyncServer(text)
        session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), listener, now = { time })
        server.connect(session)
        server.pump()
    }

    private val revision: String get() = server.document.headsHex.sorted().joinToString(".")

    private fun agentFocus(revision: String, from: Int, to: Int, viewport: String = "") = session.onMessage(
        """{"type":"presence","documentId":"${server.documentId}","clientId":"agent-0123456789ab","instanceId":"i1",
            "accountId":"a1","username":"bob","selections":[],
            "agent":{"tokenId":"t1","name":"claude","focus":{"revision":"$revision","from":$from,"to":$to$viewport}}}"""
    )

    private val agent: RemotePeer get() = peers.last().single()

    @Test
    fun `the focus of an agent is a selection and a viewport in the text of now`() {
        connect("one two three")
        val old = revision
        server.edit(">> one two three")
        server.pump()

        agentFocus(old, from = 4, to = 7, viewport = ""","viewport":{"top":0,"bottom":12}""")

        // "two" moved by the 3 characters in front of it
        assertEquals(RemoteSelection(anchor = 7, head = 10), agent.selections.single())
        assertEquals(RemoteViewport(top = 3, bottom = 15), agent.viewport)
    }

    @Test
    fun `indexes count code points, and the end of the text stays where it was`() {
        connect("a😀b")
        val old = revision
        server.edit(">a😀bcd")
        server.pump()

        agentFocus(old, from = 1, to = 3)

        // behind the emoji is UTF-16 position 4 now, and the old end is behind "b", not at the end of the text
        assertEquals(RemoteSelection(anchor = 2, head = 5), agent.selections.single())
    }

    @Test
    fun `the focus in an empty text is at the start`() {
        connect("")
        val old = revision
        server.edit("new text")
        server.pump()

        agentFocus(old, from = 0, to = 0)

        assertEquals(RemoteSelection(0, 0), agent.selections.single())
    }

    @Test
    fun `a revision that has not arrived keeps the place before, and counts in the text of now after the wait limit`() {
        connect("abcdef")
        agentFocus(revision, from = 1, to = 1)
        assertEquals(RemoteSelection(1, 1), agent.selections.single())

        // a change the session does not have yet
        val other = AutomergeTextDocument.fromDocument(server.document.document.fork())
        other.applyLocalText("abcdefgh")
        agentFocus(other.headsHex.sorted().joinToString("."), from = 7, to = 7)
        assertEquals(RemoteSelection(1, 1), agent.selections.single())

        time += AGENT_WAIT_MS
        session.sendHeartbeat()
        // clamped to the text of now
        assertEquals(RemoteSelection(6, 6), agent.selections.single())
    }

    @Test
    fun `an agent without a focus has no selection`() {
        connect("abc")
        session.onMessage(
            """{"type":"presence","documentId":"${server.documentId}","clientId":"agent-0123456789ab","instanceId":"i1",
                "accountId":"a1","username":"bob","selections":[],"agent":{"tokenId":"t1","name":"claude"}}"""
        )

        assertEquals(emptyList(), agent.selections)
        assertNull(agent.viewport)
    }
}
