package de.markusressel.mkdocsrestclient.sync.automerge

import org.automerge.Cursor
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
