package de.markusressel.mkdocsrestclient.sync.automerge

import org.automerge.Cursor
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.PatchAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins down the behaviour of automerge-java that the sync code relies on.
 * These tests also make sure the native library loads in plain JVM unit tests.
 */
class AutomergeLibraryTest {

    private fun newTextDocument(initial: String): Pair<Document, ObjectId> {
        val doc = Document()
        lateinit var text: ObjectId
        doc.startTransaction().use { tx ->
            text = tx.set(ObjectId.ROOT, "content", ObjectType.TEXT)
            tx.spliceText(text, 0, 0, initial)
            tx.commit()
        }
        return doc to text
    }

    @Test
    fun `text indices count unicode code points, not UTF-16 units`() {
        val (doc, text) = newTextDocument("a😀b")

        assertEquals(4, "a😀b".length)
        assertEquals(3, doc.length(text))

        doc.startTransaction().use { tx ->
            tx.spliceText(text, 2, 0, "X")
            tx.commit()
        }
        assertEquals("a😀Xb", doc.text(text).get())
    }

    @Test
    fun `concurrent edits of two forks merge`() {
        val (doc, text) = newTextDocument("hello world")
        val other = Document.load(doc.save())

        doc.startTransaction().use { tx ->
            tx.spliceText(text, 0, 5, "goodbye")
            tx.commit()
        }
        other.startTransaction().use { tx ->
            tx.spliceText(text, 11, 0, "!")
            tx.commit()
        }

        doc.merge(other)
        assertEquals("goodbye world!", doc.text(text).get())
    }

    @Test
    fun `cursors survive concurrent inserts and string round trips`() {
        val (doc, text) = newTextDocument("hello world")
        val cursor = doc.makeCursor(text, 6)

        doc.startTransaction().use { tx ->
            tx.spliceText(text, 0, 0, ">> ")
            tx.commit()
        }

        val decoded = Cursor.fromString(cursor.toString())
        assertEquals(9, doc.lookupCursorIndex(text, decoded))
    }

    @Test
    fun `a loaded document takes a given actor id through fork`() {
        val actor = ByteArray(16) { it.toByte() }
        val (doc, _) = newTextDocument("x")

        // Document.load has no actor parameter in automerge-java 0.0.9
        val loaded = Document.load(doc.save()).fork(actor)
        assertEquals(actor.toList(), loaded.actorId.toList())
        assertEquals(actor.toList(), Document(actor).actorId.toList())
    }

    // The positions of an agent's focus and change come from the server in code points, see docs/agent-presence.md
    // of the backend. These pin down the units and the rules the client builds on.

    @Test
    fun `a cursor made at older heads counts code points and resolves in the current text`() {
        val (doc, text) = newTextDocument("a😀bc")
        val old = doc.heads
        doc.startTransaction().use { tx ->
            tx.spliceText(text, 0, 0, "😀😀")
            tx.commit()
        }

        // 'b' is the third code point at the old heads, and the fifth now
        assertEquals(4, doc.lookupCursorIndex(text, doc.makeCursor(text, 2, old)))
        assertEquals(2, doc.lookupCursorIndex(text, doc.makeCursor(text, 2, old), old))
    }

    @Test
    fun `a cursor at the end of the text can not be made, with heads neither`() {
        val (doc, text) = newTextDocument("a😀b")
        val old = doc.heads
        doc.startTransaction().use { tx ->
            tx.spliceText(text, 3, 0, "cd")
            tx.commit()
        }

        // AutomergeException is not public
        assertEquals("index 3 is out of bounds", assertFailsWith<RuntimeException> { doc.makeCursor(text, 3, old) }.message)
        assertEquals("index 5 is out of bounds", assertFailsWith<RuntimeException> { doc.makeCursor(text, 5) }.message)
        // so the end is the cursor of the last character, plus one
        assertEquals(3, doc.lookupCursorIndex(text, doc.makeCursor(text, 2, old)) + 1)
    }

    @Test
    fun `a cursor on a character that was deleted later resolves to the next one that is left`() {
        val (doc, text) = newTextDocument("ab😀def")
        val old = doc.heads
        doc.startTransaction().use { tx ->
            tx.spliceText(text, 1, 3, "")
            tx.commit()
        }

        assertEquals("aef", doc.text(text).get())
        assertEquals(1, doc.lookupCursorIndex(text, doc.makeCursor(text, 2, old)))
    }

    @Test
    fun `only a fork tells that the document does not have some heads`() {
        val (doc, text) = newTextDocument("abc")
        val other = Document.load(doc.save())
        other.startTransaction().use { tx ->
            tx.spliceText(text, 3, 0, "d")
            tx.commit()
        }

        // the text at unknown heads comes back without an error, empty
        assertEquals("", doc.text(text, other.heads).get())
        assertEquals(
            "invalid hash ${other.heads.single().bytes.joinToString("") { "%02x".format(it) }}",
            assertFailsWith<RuntimeException> { doc.fork(other.heads) }.message,
        )
        doc.merge(other)
        assertEquals("abcd", doc.fork(other.heads).text(text).get())
    }

    @Test
    fun `the patches of a diff count code points and follow each other in the order of the text`() {
        val (doc, text) = newTextDocument("0😀23456789")
        val base = doc.heads
        doc.startTransaction().use { tx ->
            tx.spliceText(text, 8, 1, "")
            tx.spliceText(text, 1, 0, "AB")
            tx.spliceText(text, 6, 0, "Z")
            tx.spliceText(text, 0, 1, "")
            tx.commit()
        }

        assertEquals("AB😀23Z45679", doc.text(text).get())
        val patches = doc.diff(base, doc.heads).map { patch ->
            when (val action = patch.action) {
                is PatchAction.SpliceText -> "insert ${action.text} at ${action.index}"
                is PatchAction.DeleteList -> "delete ${action.length} at ${action.index}"
                else -> action.toString()
            }
        }
        // each one refers to the text after the ones before it, not to the text before the change
        assertEquals(listOf("delete 1 at 0", "insert AB at 0", "insert Z at 5", "delete 1 at 10"), patches)
    }
}
