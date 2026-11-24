package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * KodeEditor attaches the text behind an insert to the caret at the insert's end, so a peer's cursor has to reach the
 * editor in the same main thread call as the change it typed, not with its next presence (docs/reveal.md, R5b).
 */
class PeerCursorWithChangeTest {

    /** what the editor gets, in order: the text of each change, and where bob's cursor is with each peer update */
    private class Events : DocumentSyncSession.Listener {
        val log = mutableListOf<String>()
        private var text = ""

        override fun onTextReplaced(text: String) {
            this.text = text
        }

        override fun onRemoteChange(text: String, splices: List<TextSplice>, author: String?, reveals: List<AgentReveal>) {
            this.text = text
            log += "change $text"
        }

        override fun onPeersChanged(peers: List<RemotePeer>) {
            peers.singleOrNull { it.username == "bob" }?.selections?.singleOrNull()?.let { log += "bob at ${it.head} of $text" }
        }
    }

    private fun bobTypes(initial: String, caret: Int, insert: String): List<String> {
        val server = FakeSyncServer(initial)
        val alice = Events()
        server.connect(DocumentSyncSession(server.documentId, InMemoryDocumentStore(), alice), "alice")
        val bob = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), object : DocumentSyncSession.Listener {})
        server.connect(bob, "bob")
        server.pump()
        bob.updateLocalSelection(caret, caret)
        server.pump()
        alice.log.clear()

        // bob types, and his next presence has not come yet
        bob.applyLocalEdit(bob.version!!, bob.text!!, listOf(TextSplice(caret, 0, insert)))
        bob.flushSync()
        server.pump()
        return alice.log
    }

    @Test
    fun `the cursor of a peer who types inside the text comes with the change, at the end of what was typed`() {
        val log = bobTypes("hello world", caret = 5, insert = "!!!")

        val change = log.indexOf("change hello!!! world")
        assertEquals("bob at 8 of hello!!! world", log[change + 1], "$log")
    }

    @Test
    fun `the cursor of a peer who types at the end of the text comes with the change, at the new end`() {
        val log = bobTypes("hello", caret = 5, insert = " world")

        val change = log.indexOf("change hello world")
        assertEquals("bob at 11 of hello world", log[change + 1], "$log")
    }
}
