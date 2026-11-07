package de.markusressel.mkdocseditor.feature.editor

import androidx.compose.ui.text.TextRange
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentSyncSession
import de.markusressel.mkdocsrestclient.sync.automerge.FakeSyncServer
import de.markusressel.mkdocsrestclient.sync.automerge.InMemoryDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.toHex
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Actor ids from a seed instead of the system's random source. Automerge orders concurrent inserts by actor id, so
 * with random ids a run of the random tests can't be repeated.
 */
private class SeededSecureRandom(private val random: Random) : SecureRandom() {
    /** what this handed out, as hex: the first one is the actor id of the session */
    val handedOut = mutableListOf<String>()

    override fun nextBytes(bytes: ByteArray) {
        random.nextBytes(bytes)
        handedOut += bytes.toHex()
    }
}

/** One app: an [EditorTextController] on a session, connected the way `DocumentSession` does it. */
private class TypingClient(val server: FakeSyncServer, val name: String, ids: Random) : DocumentSyncSession.Listener {
    private val idSource = SeededSecureRandom(ids)
    val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), this, random = idSource)
    val controller = EditorTextController(editModeActive = { true }).also { it.bind(session) }
    val editor = controller.editorState
    var connection = server.connect(session, name)

    override fun onTextReplaced(text: String) = controller.onTextReplaced()
    override fun onRemoteChange(text: String, splices: List<TextSplice>, author: String?) =
        controller.onRemoteChange(text, splices, author)
    override fun onPeersChanged(peers: List<RemotePeer>) = controller.onPeersChanged(peers)

    val shownText get() = editor.text

    fun moveCaret(index: Int) = editor.setSelection(TextRange(index))

    fun type(input: String) = editor.replaceSelection(input)

    fun backspace() {
        val caret = editor.selection.min
        if (caret > 0) editor.edit(editor.text.removeRange(caret - 1, caret), TextRange(caret - 1))
    }

    fun send() {
        session.flushSync()
        server.pump()
    }

    val actorId: String? get() = idSource.handedOut.firstOrNull()
}

/**
 * Several clients with the real editor state, the real sync session and a fake server. Everything
 * that needs the keyboard is in [EditorInputTest].
 */
class ConcurrentTypingTest {

    /** @param ids the seed of all actor ids, so that a run can be repeated */
    private fun clients(text: String, count: Int = 2, ids: Long = 0): List<TypingClient> {
        val random = Random(ids)
        val server = FakeSyncServer(text, actorId = random.nextBytes(16))
        val clients = List(count) { TypingClient(server, "user$it", random) }
        server.pump()
        return clients
    }

    /**
     * Syncs until every client has the state of the server. [FakeSyncServer.pump] already delivers until nothing is
     * left, so this takes one round, two when a client still has a change it has not sent.
     *
     * @return the rounds it took
     */
    private fun settle(clients: List<TypingClient>): Int {
        val server = clients.first().server
        for (round in 1..MAX_SETTLE_ROUNDS) {
            clients.forEach { it.session.flushSync() }
            server.pump()
            if (clients.all { it.session.version?.hasHeads(server.document.headsHex) == true }) return round
        }
        fail("sync did not settle after $MAX_SETTLE_ROUNDS rounds\n${describe(clients)}")
    }

    private fun assertAllEqual(clients: List<TypingClient>, message: String = "") {
        val text = clients.first().session.text
        val state = describe(clients)
        clients.forEach {
            assertEquals(text, it.session.text, "$message clients differ\n$state")
            assertEquals(text, it.shownText, "$message editor differs from the document\n$state")
            assertEquals(text, it.server.document.text, "$message server differs\n$state")
        }
    }

    /** Everything needed to understand a failure, with the typed characters made readable */
    private fun describe(clients: List<TypingClient>): String {
        val server = clients.first().server
        return buildString {
            appendLine("server: actor ${server.document.actorId.toHex()}, heads ${server.document.headsHex.map { it.take(8) }}")
            appendLine("  text: ${readable(server.document.text)}")
            clients.forEach {
                appendLine("${it.name}: actor ${it.actorId}, heads ${it.session.version}, unsynced ${it.session.hasUnsyncedChanges}")
                appendLine("  document: ${it.session.text?.let(::readable)}")
                appendLine("  editor:   ${readable(it.shownText)}")
            }
        }
    }

    /** a typed character as <client.n>, see [letter] */
    private fun readable(text: String): String = buildString {
        text.forEach { c ->
            val owner = ownerOf(c)
            if (owner < 0) append(if (c == '\n') "\\n" else c.toString())
            else append("<$owner.${c.code - 0xE000 - owner * 0x800}>")
        }
    }

    @Test
    fun `a remote change shows at once, and a key press right after it keeps both`() {
        val clients = clients("Hello")
        val (a, b) = clients
        a.moveCaret(5); b.moveCaret(5)

        b.type("X")
        b.send()
        assertEquals("HelloX", a.shownText)
        a.type("Y")
        a.send()

        settle(clients)
        // the caret does not move with text inserted right at it
        assertEquals("HelloYX", a.shownText)
        assertAllEqual(clients)
    }

    @Test
    fun `several key presses after a remote change keep their order`() {
        val clients = clients("0123456789")
        val (a, b) = clients
        a.moveCaret(5)

        b.moveCaret(0)
        b.type("remote")
        b.send()
        a.type("a"); a.type("b"); a.type("c")
        a.send()

        settle(clients)
        assertEquals("remote01234abc56789", a.session.text)
        assertAllEqual(clients)
        assertEquals("remote01234abc".length, a.editor.selection.start)
    }

    @Test
    fun `the caret moves with changes before it and stays with changes behind it`() {
        val (a, b) = clients("Hello world")
        a.moveCaret(6)

        b.moveCaret(0); b.type(">> ")
        b.send()
        assertEquals(9, a.editor.selection.start)

        b.moveCaret(14); b.type("!")
        b.send()
        assertEquals(">> Hello world!", a.shownText)
        assertEquals(9, a.editor.selection.start)

        repeat(3) { b.moveCaret(3 - it); b.backspace() }
        b.send()
        assertEquals("Hello world!", a.shownText)
        assertEquals(6, a.editor.selection.start)
    }

    @Test
    fun `a selection keeps its characters`() {
        val (a, b) = clients("Hello world")
        a.editor.setSelection(TextRange(6, 11))

        b.moveCaret(0); b.type("__")
        b.send()

        assertEquals("world", a.controller.selectedText)
    }

    @Test
    fun `typing an equal letter goes where the caret is`() {
        val (a, b) = clients("aaa")
        a.moveCaret(1)
        b.moveCaret(2)
        b.type("X")
        b.send()
        assertEquals("aaXa", a.shownText)
        // the text alone looks like the "a" was typed at the start of the run
        a.type("a")
        a.send()
        assertEquals("aaaXa", a.session.text)
        assertEquals(2, a.editor.selection.start)
    }

    @Test
    fun `emoji and umlauts in concurrent edits`() {
        val clients = clients("😀ä😀")
        val (a, b) = clients
        a.moveCaret(2)
        b.moveCaret(5)
        b.type("🎉")
        b.send()
        a.type("ö")
        a.send()
        settle(clients)
        assertEquals("😀öä😀🎉", a.session.text)
        assertAllEqual(clients)
    }

    @Test
    fun `an edit from a toolbar keeps its selection while remote changes arrive`() {
        val clients = clients("word here")
        val (a, b) = clients
        a.editor.setSelection(TextRange(0, 4))
        a.controller.applyFormat(FormatAction.Bold)
        a.send()
        assertEquals("**word** here", b.shownText)

        b.moveCaret(13); b.type("!")
        b.send()

        assertEquals("**word** here!", a.shownText)
        assertEquals("word", a.controller.selectedText)
        settle(clients)
        assertAllEqual(clients)
    }

    @Test
    fun `undo reaches the other client`() {
        val clients = clients("base")
        val (a, b) = clients
        a.moveCaret(4)
        a.type("!")
        a.send()
        assertEquals("base!", b.shownText)
        a.editor.undo()
        settle(clients)
        assertEquals("base", b.shownText)
        assertAllEqual(clients)
    }

    @Test
    fun `offline edits merge on reconnect while the user keeps typing`() {
        val clients = clients("base\n")
        val (a, b) = clients
        a.moveCaret(5)
        val server = a.server
        server.disconnect(a.connection)

        a.type("offline ")
        b.moveCaret(0); b.type("online ")
        b.send()

        a.connection = server.connect(a.session, "user0")
        server.pump()
        a.type("x")
        settle(clients)

        assertEquals("online base\noffline x", a.session.text)
        assertAllEqual(clients)
    }

    @Test
    fun `a new epoch moves the unsynced edits and the editor along`() {
        val (a, _) = clients("one\n")
        a.moveCaret(4)
        val server = a.server
        a.send()
        server.restartWithLostState("zero\none\n")
        a.type("local")
        a.connection = server.connect(a.session, "user0")
        server.pump()
        a.type("!")
        a.send()

        assertEquals("zero\none\nlocal!", a.session.text)
        assertEquals(a.session.text, a.shownText)
        assertEquals(server.document.text, a.session.text)
    }

    @Test
    fun `a read-only document refuses input and the editor goes back`() {
        val server = FakeSyncServer("read me", readOnly = true)
        val a = TypingClient(server, "reader", Random(0))
        server.pump()
        a.type("x")
        assertEquals("read me", a.shownText)
        assertEquals("read me", a.session.text)
    }

    @Test
    fun `no document means no edits`() {
        val server = FakeSyncServer("text")
        val a = TypingClient(server, "user", Random(0))
        assertNull(a.session.text)
        a.controller.showDetachedText("cached")
        assertEquals("cached", a.shownText)
        a.type("x")
        assertEquals("cached", a.shownText)
        server.pump()
        assertEquals("text", a.shownText)
        assertEquals("text", a.session.text)
    }

    /**
     * Random interleavings: every client types marked characters, network deliveries come at
     * random moments. Afterwards all clients have the same text, and it holds every typed character.
     */
    @Test
    fun `random interleavings lose nothing and converge`() {
        for (seed in 0 until 200) {
            val random = Random(seed)
            val count = 2 + random.nextInt(2)
            val clients = clients("Hello world\nsecond line\n", count, ids = seed.toLong())
            val typed = IntArray(count)
            clients.forEach { it.moveCaret(random.nextInt(it.shownText.length + 1)) }
            repeat(150) {
                val i = random.nextInt(count)
                val client = clients[i]
                when (random.nextInt(7)) {
                    in 0..3 -> client.type(letter(i, typed[i]++).toString())
                    4, 5 -> client.send()
                    6 -> if (random.nextInt(8) == 0) client.moveCaret(random.nextInt(client.shownText.length + 1))
                }
            }
            val rounds = settle(clients)
            assertAllEqual(clients, "seed $seed, $count clients, settled in $rounds rounds:")
            val text = clients.first().shownText
            for (i in 0 until count) {
                val expected = (0 until typed[i]).map { letter(i, it) }.toSet()
                assertEquals(expected, text.filter { ownerOf(it) == i }.toSet(), "seed $seed: client $i lost characters")
            }
        }
    }

    /** random deletes: convergence, and the editor always equals the document */
    @Test
    fun `random typing and deleting converges`() {
        for (seed in 0 until 100) {
            val random = Random(1000 + seed)
            val clients = clients("0123456789\nabcdefghij\n", ids = 1000L + seed)
            repeat(150) {
                val client = clients[random.nextInt(2)]
                when (random.nextInt(6)) {
                    0, 1 -> client.type("x")
                    2 -> client.backspace()
                    3, 4 -> client.send()
                    5 -> if (random.nextInt(5) == 0) client.moveCaret(random.nextInt(client.shownText.length + 1))
                }
                assertEquals(client.session.text, client.shownText, "seed $seed")
            }
            val rounds = settle(clients)
            assertAllEqual(clients, "seed ${1000 + seed}, settled in $rounds rounds:")
        }
    }

    @Test
    fun `peers follow the text of the editor`() {
        val clients = clients("Text ")
        val (a, b) = clients
        a.moveCaret(5); b.moveCaret(5)
        b.session.updateLocalSelection(5, 5)
        b.send()
        assertEquals(5, a.controller.peers.value.single().selections.single().head)

        a.moveCaret(0)
        a.type("REMOTE ")
        assertEquals("REMOTE Text ", a.shownText)
        assertEquals(12, a.controller.peers.value.single().selections.single().head)
        assertTrue(a.controller.peers.value.single().selections.single().head <= a.shownText.length)
    }

    /** private use area, so a character tells who typed it and they are all different */
    private fun letter(client: Int, n: Int): Char = (0xE000 + client * 0x800 + n).toChar()

    private fun ownerOf(c: Char): Int = if (c.code in 0xE000..0xF7FF) (c.code - 0xE000) / 0x800 else -1

    private companion object {
        const val MAX_SETTLE_ROUNDS = 20
    }
}
