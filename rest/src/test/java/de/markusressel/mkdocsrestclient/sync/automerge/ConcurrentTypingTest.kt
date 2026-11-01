package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The text field of Compose as far as it matters here: a buffer that takes input right away and
 * the value from outside only in a frame (see `EditProcessor` and `CoreTextField` in Compose).
 * Getting a new text commits the composition and restarts the input method.
 */
private class SimulatedField(initial: EditorFieldValue) {
    var buffer = initial
    var imeRestarts = 0
    var committedCompositions = 0
    var onValue: (EditorFieldValue) -> Unit = {}

    fun type(input: String) {
        val b = buffer
        val text = b.text.substring(0, b.selectionStart) + input + b.text.substring(b.selectionEnd)
        val caret = b.selectionStart + input.length
        emit(EditorFieldValue(text, caret))
    }

    fun backspace() {
        val b = buffer
        if (b.selectionStart == 0) return
        emit(
            EditorFieldValue(
                b.text.removeRange(b.selectionStart - 1, b.selectionStart),
                b.selectionStart - 1,
            )
        )
    }

    /** setComposingText: replaces the composing region, or starts one at the caret */
    fun compose(composing: String) {
        val b = buffer
        val start = b.compositionStart ?: b.selectionStart
        val end = b.compositionEnd ?: b.selectionEnd
        val text = b.text.substring(0, start) + composing + b.text.substring(end)
        val caret = start + composing.length
        emit(EditorFieldValue(text, caret, caret, start, caret))
    }

    /** setComposingRegion */
    fun composeRegion(start: Int, end: Int) = emit(buffer.copy(compositionStart = start, compositionEnd = end))

    /** finishComposingText */
    fun finishComposing() = emit(buffer.copy(compositionStart = null, compositionEnd = null))

    fun moveCaret(index: Int) = emit(buffer.copy(selectionStart = index, selectionEnd = index))

    private fun emit(value: EditorFieldValue) {
        buffer = value
        onValue(value)
    }

    /** `EditProcessor.reset` */
    fun recompose(value: EditorFieldValue) {
        if (value.text != buffer.text) {
            imeRestarts++
            if (buffer.isComposing) committedCompositions++
            buffer = value.copy(compositionStart = null, compositionEnd = null)
        } else if (value.compositionStart == null && buffer.isComposing) {
            imeRestarts++
            committedCompositions++
            buffer = value
        } else {
            buffer = value
        }
    }
}

/**
 * One app: an [EditorTextBinding] on a session, with a field that lags behind.
 */
private class TypingClient(val server: FakeSyncServer, name: String, val clock: () -> Long) :
    DocumentSyncSession.Listener {
    val session = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), this)
    val binding = EditorTextBinding(session, clock)
    val field = SimulatedField(binding.value).also { it.onValue = binding::onFieldChanged }
    var connection = server.connect(session, name)

    override fun onTextReplaced(text: String) = binding.onDocumentChanged()
    override fun onRemoteChange(text: String, splices: List<TextSplice>) = binding.onDocumentChanged(splices)

    /** a frame: the field takes the value, and the app learns that it did */
    fun frame() {
        binding.flush()
        field.recompose(binding.value)
        binding.onValueShown(binding.valueId)
    }

    val shownText get() = field.buffer.text
}

class ConcurrentTypingTest {

    private var time = 1_000_000L
    private val clock = { time }

    private fun clients(text: String, count: Int = 2): List<TypingClient> {
        val server = FakeSyncServer(text)
        val clients = List(count) { TypingClient(server, "user$it", clock) }
        server.pump()
        clients.forEach {
            it.frame()
            it.field.imeRestarts = 0
        }
        return clients
    }

    private fun TypingClient.send() {
        session.flushSync()
        server.pump()
    }

    private fun settle(clients: List<TypingClient>) {
        repeat(3) {
            clients.forEach { it.session.flushSync() }
            clients.first().server.pump()
        }
        time += maxOf(EditorTextBinding.COMPOSING_IDLE_MS, EditorTextBinding.MIN_RESTART_INTERVAL_MS) + 1
        clients.forEach { it.frame() }
    }

    @Test
    fun `a key press before the next frame keeps the remote change`() {
        val (a, b) = clients("Hello")
        a.field.moveCaret(5); b.field.moveCaret(5)
        a.frame(); b.frame()

        b.field.type("X")
        b.send()
        // X reached A, but its field has not taken the new text yet when the user types
        assertEquals("Hello", a.shownText)
        assertEquals("HelloX", a.session.text)
        a.field.type("Y")
        a.send()

        assertTrue(a.session.text!!.contains('X'), a.session.text)
        assertTrue(a.session.text!!.contains('Y'), a.session.text)
        settle(listOf(a, b))
        assertEquals(a.session.text, b.session.text)
        assertEquals(a.session.text, a.shownText)
        assertEquals(b.session.text, b.shownText)
    }

    @Test
    fun `several key presses before the next frame keep their order`() {
        val (a, b) = clients("0123456789")
        a.field.moveCaret(5)
        a.frame()

        b.field.moveCaret(0)
        b.field.type("remote")
        b.send()
        a.field.type("a"); a.field.type("b"); a.field.type("c")
        a.send()

        settle(listOf(a, b))
        assertEquals("remote01234abc56789", a.session.text)
        assertEquals(a.session.text, b.session.text)
        // the caret is behind the typed text
        assertEquals("remote01234abc".length, a.field.buffer.selectionStart)
    }

    @Test
    fun `the caret moves with changes before it and stays with changes behind it`() {
        val (a, b) = clients("Hello world")
        a.field.moveCaret(6)
        a.frame()

        b.field.moveCaret(0); b.field.type(">> ")
        b.send()
        a.frame()
        assertEquals(9, a.field.buffer.selectionStart)

        b.field.moveCaret(14); b.field.type("!")
        b.send()
        a.frame()
        assertEquals(">> Hello world!", a.shownText)
        assertEquals(9, a.field.buffer.selectionStart)

        repeat(3) { b.field.moveCaret(3 - it); b.field.backspace() }
        b.send()
        a.frame()
        assertEquals("Hello world!", a.shownText)
        assertEquals(6, a.field.buffer.selectionStart)
    }

    @Test
    fun `a selection keeps its characters`() {
        val (a, b) = clients("Hello world")
        a.field.buffer = a.field.buffer.copy(selectionStart = 6, selectionEnd = 11)
        a.binding.onFieldChanged(a.field.buffer)
        a.frame()

        b.field.moveCaret(0); b.field.type("__")
        b.send()
        a.frame()

        val selection = a.field.buffer
        assertEquals("world", selection.text.substring(selection.selectionStart, selection.selectionEnd))
    }

    @Test
    fun `a remote change during composition waits and does not touch the composition`() {
        val (a, b) = clients("Text ")
        a.field.moveCaret(5)
        a.frame()

        a.field.compose("Mü")
        a.frame()
        b.field.moveCaret(0); b.field.type("REMOTE ")
        b.send()
        time += 50
        a.frame()

        assertEquals(0, a.field.imeRestarts)
        assertTrue(a.field.buffer.isComposing)
        assertEquals("Text Mü", a.shownText)
        assertEquals("REMOTE Text Mü", a.session.text)

        // the user keeps composing, the document gets every state of the word
        a.field.compose("Müll")
        a.frame()
        assertEquals("REMOTE Text Müll", a.session.text)
        assertEquals(0, a.field.imeRestarts)
        assertEquals(5, a.field.buffer.compositionStart)

        a.field.compose("Müller")
        a.field.finishComposing()
        assertEquals("REMOTE Text Müller", a.session.text)
        a.send()
        time += EditorTextBinding.TYPING_IDLE_MS
        a.frame()

        assertEquals("REMOTE Text Müller", a.shownText)
        assertEquals(0, a.field.committedCompositions)
        assertEquals("REMOTE Text Müller".length, a.field.buffer.selectionStart)
        settle(listOf(a, b))
        assertEquals(a.session.text, b.session.text)
    }

    @Test
    fun `a stale composition shows waiting changes after a while`() {
        val (a, b) = clients("Text ")
        a.field.moveCaret(5)
        a.frame()
        a.field.compose("wo")
        a.frame()
        b.field.moveCaret(0); b.field.type("R")
        b.send()

        val delay = a.binding.holdDelay()
        assertEquals(EditorTextBinding.COMPOSING_IDLE_MS, delay)
        time += delay!!
        a.frame()

        assertEquals("RText wo", a.shownText)
        assertEquals("RText wo", a.session.text)
    }

    @Test
    fun `an autocorrect replacing a word keeps a concurrent edit in the same line`() {
        val (a, b) = clients("teh cat")
        a.field.moveCaret(3)
        a.frame()
        a.field.composeRegion(0, 3)
        b.field.moveCaret(7); b.field.type("s")
        b.send()
        a.field.compose("the")
        a.field.finishComposing()
        a.send()
        settle(listOf(a, b))
        assertEquals("the cats", a.session.text)
        assertEquals(a.session.text, b.session.text)
    }

    @Test
    fun `typing an equal letter goes where the caret is`() {
        val (a, b) = clients("aaa")
        a.field.moveCaret(1)
        b.field.moveCaret(2)
        a.frame(); b.frame()
        b.field.type("X")
        b.send()
        a.frame()
        assertEquals("aaXa", a.shownText)
        // the text alone looks like the "a" was typed at the start of the run
        a.field.moveCaret(1)
        a.frame()
        a.field.type("a")
        a.send()
        assertEquals("aaaXa", a.session.text)
        assertEquals(2, a.field.buffer.selectionStart)
    }

    @Test
    fun `emoji and umlauts in concurrent edits`() {
        val (a, b) = clients("😀ä😀")
        a.field.moveCaret(2)
        b.field.moveCaret(5)
        a.frame(); b.frame()
        b.field.type("🎉")
        b.send()
        a.field.type("ö")
        a.send()
        settle(listOf(a, b))
        assertEquals("😀öä😀🎉", a.session.text)
        assertEquals(a.session.text, b.session.text)
    }

    @Test
    fun `peers are placed on the text the field shows`() {
        val (a, b) = clients("Text ")
        a.field.moveCaret(5)
        a.frame()
        a.field.compose("wo")
        b.field.moveCaret(0); b.field.type("REMOTE ")
        b.send()

        // in the document the peer is behind REMOTE, in the field at the start of the line
        assertEquals("REMOTE Text wo", a.session.text)
        assertEquals("Text wo", a.binding.value.text)
        assertEquals(0, a.binding.toFieldIndex(7))
        assertEquals(5, a.binding.toFieldIndex(12))
        // the caret of this client in the document text
        assertEquals(14, a.binding.documentSelectionStart)
    }

    @Test
    fun `with an input method new texts come at most once in a while`() {
        val (a, b) = clients("Text")
        a.binding.inputMethodActive = true
        a.field.moveCaret(4)
        a.frame()
        b.field.moveCaret(0)

        b.field.type("1")
        b.send()
        a.frame()
        assertEquals("1Text", a.shownText)

        time += 100
        b.field.type("2")
        b.send()
        a.frame()
        assertEquals("1Text", a.shownText)
        assertEquals("12Text", a.session.text)

        val delay = a.binding.holdDelay()
        assertEquals(EditorTextBinding.MIN_RESTART_INTERVAL_MS - 100, delay)
        time += delay!!
        a.frame()
        assertEquals("12Text", a.shownText)
        assertEquals(1, a.field.imeRestarts - 1)
    }

    @Test
    fun `an edit from a toolbar sets text and selection while remote changes arrive`() {
        val (a, b) = clients("word here")
        a.field.moveCaret(0)
        a.frame()
        assertTrue(a.binding.applyEdit("**word** here", 2, 6))
        // a change arrives before the field took the edit
        b.field.moveCaret(9); b.field.type("!")
        b.send()
        a.frame()
        assertEquals("**word** here!", a.shownText)
        assertEquals(2, a.field.buffer.selectionStart)
        assertEquals(6, a.field.buffer.selectionEnd)
        settle(listOf(a, b))
        assertEquals(a.session.text, b.session.text)
    }

    @Test
    fun `offline edits merge on reconnect while the user keeps typing`() {
        val (a, b) = clients("base\n")
        a.field.moveCaret(5)
        a.frame()
        val server = a.server
        server.disconnect(a.connection)

        a.field.type("offline ")
        time += 1000; a.frame()
        b.field.moveCaret(0); b.field.type("online ")
        b.send()

        a.connection = server.connect(a.session, "user0")
        server.pump()
        // the merged text arrives, and the next key press is older than the next frame
        a.field.type("x")
        a.session.flushSync()
        server.pump()
        settle(listOf(a, b))

        assertEquals("online base\noffline x", a.session.text)
        assertEquals(a.session.text, b.session.text)
        assertEquals(a.session.text, a.shownText)
    }

    @Test
    fun `a new epoch moves the unsynced edits and the field along`() {
        val (a, _) = clients("one\n")
        a.field.moveCaret(4)
        a.frame()
        val server = a.server
        a.session.flushSync(); server.pump()
        server.restartWithLostState("zero\none\n")
        a.field.type("local")
        a.connection = server.connect(a.session, "user0")
        server.pump()
        // a key press before the field got the new text
        a.field.type("!")
        a.session.flushSync(); server.pump()
        time += 1000
        a.frame()

        assertEquals("zero\none\nlocal!", a.session.text)
        assertEquals(a.session.text, a.shownText)
        assertEquals(server.document.text, a.session.text)
    }

    @Test
    fun `read-only documents refuse input and the field goes back`() {
        val server = FakeSyncServer("read me", readOnly = true)
        val a = TypingClient(server, "reader", clock)
        server.pump()
        a.frame()
        a.field.type("x")
        time += 1000
        a.frame()
        assertEquals("read me", a.shownText)
        assertEquals("read me", a.session.text)
    }

    @Test
    fun `no document means no edits`() {
        val server = FakeSyncServer("text")
        val a = TypingClient(server, "user", clock)
        assertNull(a.session.text)
        a.binding.showDetachedText("cached")
        assertEquals("cached", a.binding.value.text)
        a.frame()
        assertFalse(a.binding.applyEdit("cached!", 0, 0))
        server.pump()
        time += 1000
        a.frame()
        assertEquals("text", a.shownText)
    }

    /**
     * Random interleavings: every client types marked characters, frames and network deliveries
     * come at random moments. Afterwards all clients have the same text, and it holds every
     * typed character, in the order each client typed it into a place.
     */
    @Test
    fun `random interleavings lose nothing and converge`() {
        for (seed in 0 until 200) {
            val random = Random(seed)
            val count = 2 + random.nextInt(2)
            val cs = clients("Hello world\nsecond line\n", count)
            val typed = IntArray(count)
            cs.forEach { c ->
                c.binding.inputMethodActive = random.nextBoolean()
                val index = random.nextInt(c.shownText.length + 1)
                c.field.moveCaret(index)
                c.frame()
            }
            repeat(150) {
                time += random.nextLong(0, 40)
                val i = random.nextInt(count)
                val c = cs[i]
                when (random.nextInt(8)) {
                    in 0..2 -> {
                        // each client types its own alphabet, in order
                        c.field.type(letter(i, typed[i]++).toString())
                    }

                    3 -> if (random.nextInt(4) == 0) {
                        val word = letter(i, typed[i]).toString() + letter(i, typed[i] + 1)
                        typed[i] += 2
                        c.field.compose(word.take(1))
                        c.field.compose(word)
                        c.field.finishComposing()
                    }

                    4, 5 -> c.frame()
                    6 -> c.send()
                    7 -> if (random.nextInt(8) == 0) {
                        c.field.moveCaret(random.nextInt(c.field.buffer.text.length + 1))
                    }
                }
            }
            settle(cs)
            val text = cs[0].session.text!!
            cs.forEach {
                assertEquals(text, it.session.text, "seed $seed: clients differ")
                assertEquals(text, it.shownText, "seed $seed: field differs from the document")
                assertEquals(text, it.server.document.text, "seed $seed: server differs")
            }
            for (i in 0 until count) {
                val kept = text.filter { c -> ownerOf(c) == i }
                assertEquals(
                    typed[i],
                    kept.length,
                    "seed $seed: client $i typed ${typed[i]} characters, the text has: $text",
                )
                val expected = (0 until typed[i]).map { letter(i, it) }.toSet()
                assertEquals(expected, kept.toSet(), "seed $seed: client $i lost characters")
            }
        }
    }

    /** random deletes: convergence, and characters nobody deleted stay */
    @Test
    fun `random typing and deleting converges`() {
        for (seed in 0 until 100) {
            val random = Random(1000 + seed)
            val cs = clients("0123456789\nabcdefghij\n")
            repeat(150) {
                time += random.nextLong(0, 40)
                val c = cs[random.nextInt(2)]
                when (random.nextInt(7)) {
                    0, 1 -> c.field.type("x")
                    2 -> c.field.backspace()
                    3, 4 -> c.frame()
                    5 -> c.send()
                    6 -> if (random.nextInt(5) == 0) c.field.moveCaret(random.nextInt(c.field.buffer.text.length + 1))
                }
            }
            settle(cs)
            assertEquals(cs[0].session.text, cs[1].session.text, "seed $seed")
            assertEquals(cs[0].session.text, cs[0].shownText, "seed $seed")
            assertEquals(cs[1].session.text, cs[1].shownText, "seed $seed")
        }
    }

    /**
     * What this test guards against, as the editor did it before [EditorTextBinding]: the text of
     * the field compared with the current document. Proves the scenario above finds the bug.
     */
    @Test
    fun `comparing the field with the current document deletes remote changes`() {
        val server = FakeSyncServer("Hello")
        val a = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), object : DocumentSyncSession.Listener {})
        val b = DocumentSyncSession(server.documentId, InMemoryDocumentStore(), object : DocumentSyncSession.Listener {})
        server.connect(a, "a")
        server.connect(b, "b")
        server.pump()

        b.onLocalTextChanged("HelloX")
        b.flushSync()
        server.pump()
        assertEquals("HelloX", a.text)
        // the field of A still shows "Hello" when the user types
        a.onLocalTextChanged("HelloY")
        a.flushSync()
        server.pump()

        assertEquals("HelloY", b.text)
    }

    /** private use area, so a character tells who typed it and they are all different */
    private fun letter(client: Int, n: Int): Char = (0xE000 + client * 0x800 + n).toChar()

    private fun ownerOf(c: Char): Int = if (c.code in 0xE000..0xF7FF) (c.code - 0xE000) / 0x800 else -1
}
