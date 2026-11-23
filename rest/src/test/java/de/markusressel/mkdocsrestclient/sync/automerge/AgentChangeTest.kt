package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A live change of an agent is announced with `agent-change`, see docs/agent-presence.md of the backend */
class AgentChangeTest {

    private var time = 0L
    private val reveals = mutableListOf<List<AgentReveal>>()
    private var text = ""
    private val listener = object : DocumentSyncSession.Listener {
        override fun onTextReplaced(text: String) {
            this@AgentChangeTest.text = text
        }

        override fun onRemoteChange(text: String, splices: List<TextSplice>, author: String?, reveals: List<AgentReveal>) {
            this@AgentChangeTest.text = text
            this@AgentChangeTest.reveals += reveals
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

    /** the agent edits on the server, and the server announces it before the sync */
    private fun agentEdits(newText: String, announce: Boolean = true) {
        val base = server.document.headsHex.sorted().joinToString(".")
        server.edit(newText)
        val change = server.document.headsHex.single()
        if (announce) {
            session.onMessage(
                """{"type":"agent-change","documentId":"${server.documentId}","clientId":"agent-0123456789ab",
                    "instanceId":"i1","agent":{"tokenId":"t1","name":"claude"},"base":"$base","change":"$change"}"""
            )
        }
        server.pump()
    }

    @Test
    fun `the text an agent inserted comes with the change that brings it`() {
        connect("hello world")

        agentEdits("hello brave new world")

        assertEquals("hello brave new world", text)
        assertEquals(listOf(AgentReveal("agent-0123456789ab", "i1", listOf(6 until 16))), reveals.flatten())
    }

    @Test
    fun `several inserts of one change, a replacement and an emoji count in the text of now`() {
        connect("a😀b c d")

        agentEdits("X a😀b😀😀 c YYd")

        // "X " at 0, "😀😀" behind the b, "YY" before the d; the deleted space is shown at once
        assertEquals(listOf(0 until 2, 6 until 10, 13 until 15), reveals.flatten().single().ranges)
    }

    @Test
    fun `a change without an announcement, or one that came late, shows at once`() {
        connect("abc")
        agentEdits("abcdef", announce = false)
        assertTrue(reveals.flatten().isEmpty())

        val base = server.document.headsHex.sorted().joinToString(".")
        server.edit("abcdefgh")
        val change = server.document.headsHex.single()
        server.pump()
        session.onMessage(
            """{"type":"agent-change","documentId":"${server.documentId}","clientId":"agent-0123456789ab",
                "base":"$base","change":"$change"}"""
        )
        server.edit("abcdefghij")
        server.pump()
        assertTrue(reveals.flatten().isEmpty())
    }

    @Test
    fun `an announcement waits at most the wait limit`() {
        connect("abc")
        val base = server.document.headsHex.sorted().joinToString(".")
        val other = AutomergeTextDocument.fromDocument(server.document.document.fork())
        other.applyLocalText("abcdef")
        session.onMessage(
            """{"type":"agent-change","documentId":"${server.documentId}","clientId":"agent-0123456789ab",
                "base":"$base","change":"${other.headsHex.single()}"}"""
        )

        time += AGENT_WAIT_MS
        session.sendHeartbeat()
        server.document.merge(other)
        server.edit(server.document.text)
        server.pump()

        assertEquals("abcdef", text)
        assertTrue(reveals.flatten().isEmpty())
    }
}
