package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.applySplices
import de.markusressel.mkdocsrestclient.sync.text.transformIndex
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.SyncState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AutomergeTextDocumentTest {

    private fun serverDocument(initial: String): ByteArray {
        val doc = Document()
        doc.startTransaction().use { tx ->
            val text = tx.set(ObjectId.ROOT, AutomergeTextDocument.CONTENT_KEY, ObjectType.TEXT)
            tx.spliceText(text, 0, 0, initial)
            tx.commit()
        }
        return doc.save()
    }

    /**
     * Runs the Automerge sync loop between two documents until neither has anything to send.
     * Returns the splices each side observed.
     */
    private fun sync(a: AutomergeTextDocument, b: AutomergeTextDocument): Pair<List<TextSplice>, List<TextSplice>> {
        val stateA = SyncState()
        val stateB = SyncState()
        val splicesA = mutableListOf<TextSplice>()
        val splicesB = mutableListOf<TextSplice>()
        var rounds = 0
        while (true) {
            val toB = a.generateSyncMessage(stateA)
            if (toB != null) splicesB += b.receiveSyncMessage(stateB, toB)
            val toA = b.generateSyncMessage(stateB)
            if (toA != null) splicesA += a.receiveSyncMessage(stateA, toA)
            if (toA == null && toB == null) break
            check(++rounds < 100) { "sync did not converge" }
        }
        return splicesA to splicesB
    }

    @Test
    fun `local edits are applied as minimal splices, so concurrent edits merge`() {
        val bytes = serverDocument("The quick brown fox")
        val alice = AutomergeTextDocument.load(bytes)
        val bob = AutomergeTextDocument.load(bytes)

        alice.applyLocalText("The slow brown fox")
        bob.applyLocalText("The quick brown fox jumps")

        sync(alice, bob)

        assertEquals("The slow brown fox jumps", alice.text)
        assertEquals("The slow brown fox jumps", bob.text)
    }

    @Test
    fun `remote changes arrive as splices that reproduce the new text`() {
        val bytes = serverDocument("line one\nline two\n")
        val local = AutomergeTextDocument.load(bytes)
        val remote = AutomergeTextDocument.load(bytes)

        remote.applyLocalText("# Title\nline one\nline 2\n")
        val before = local.text
        val (localSplices, _) = sync(local, remote)

        assertEquals(local.text, before.applySplices(localSplices))
        assertEquals("# Title\nline one\nline 2\n", local.text)
    }

    @Test
    fun `cursor stays on the same character when a remote edit lands before it`() {
        val bytes = serverDocument("hello world")
        val local = AutomergeTextDocument.load(bytes)
        val remote = AutomergeTextDocument.load(bytes)

        // local caret before "world"
        val caret = 6
        remote.applyLocalText("oh, hello world")
        val (splices, _) = sync(local, remote)

        val newCaret = transformIndex(caret, splices)
        assertEquals("world", local.text.substring(newCaret))
    }

    @Test
    fun `concurrent local typing is kept while remote changes arrive`() {
        val bytes = serverDocument("abc")
        val local = AutomergeTextDocument.load(bytes)
        val remote = AutomergeTextDocument.load(bytes)

        // the user types "X" at the end while the remote inserts at the start
        local.applyLocalText("abcX")
        remote.applyLocalText(">abc")
        val before = local.text
        val (splices, _) = sync(local, remote)

        assertEquals(">abcX", local.text)
        assertEquals(local.text, before.applySplices(splices))
        // caret after the typed X moves with the remote insert
        assertEquals(5, transformIndex(4, splices))
    }

    @Test
    fun `non BMP characters are converted between UTF-16 and code points`() {
        val bytes = serverDocument("a😀b")
        val local = AutomergeTextDocument.load(bytes)
        val remote = AutomergeTextDocument.load(bytes)

        // insert after the emoji: UTF-16 index 3, code point index 2
        local.applyLocalText("a😀Xb")
        remote.applyLocalText("🎉a😀b")
        val (splices, _) = sync(local, remote)

        assertEquals("🎉a😀Xb", local.text)
        assertEquals(listOf(TextSplice(0, 0, "🎉")), splices)
        assertEquals(local.text, remote.text)
    }

    @Test
    fun `cursors resolve to UTF-16 positions across edits`() {
        val bytes = serverDocument("😀 hello")
        val local = AutomergeTextDocument.load(bytes)
        val remote = AutomergeTextDocument.load(bytes)

        val cursor = remote.makeCursor(3) // before "hello"
        local.applyLocalText("😀😀 hello")
        sync(local, remote)

        val index = requireNotNull(local.lookupCursor(cursor))
        assertEquals("hello", local.text.substring(index))
    }

    @Test
    fun `an unknown cursor does not resolve`() {
        val local = AutomergeTextDocument.load(serverDocument("abc"))
        val other = AutomergeTextDocument.load(serverDocument("xyz"))
        assertNull(local.lookupCursor(other.makeCursor(1)))
        assertNull(local.lookupCursor("not a cursor"))
    }

    @Test
    fun `load uses the given actor id`() {
        val actor = ByteArray(16) { (it + 1).toByte() }
        val doc = AutomergeTextDocument.load(serverDocument("abc"), actor)
        assertEquals(actor.toList(), doc.actorId.toList())
    }

    @Test
    fun `a document without content is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            AutomergeTextDocument.load(Document().save())
        }
    }
}
