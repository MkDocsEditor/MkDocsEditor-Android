package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocsrestclient.sync.automerge.AgentReveal
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import androidx.compose.ui.text.TextRange
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Agents write in chunks of a few words, often while the last one is still being typed out. Each chunk joins a queue
 * behind the hidden text of its agent, and nothing shows at once (KodeEditorReveal of KodeEditor 9.6.0).
 */
class AgentChunksTest {

    private class Doc(text: String) : EditableTextDocument {
        val automerge: AutomergeTextDocument = run {
            val document = Document()
            document.startTransaction().use { tx ->
                val content = tx.set(ObjectId.ROOT, AutomergeTextDocument.CONTENT_KEY, ObjectType.TEXT)
                tx.spliceText(content, 0, 0, text)
                tx.commit()
            }
            AutomergeTextDocument.fromDocument(document)
        }
        override val text: String get() = automerge.text
        override val version: DocumentVersion get() = automerge.version
        override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) =
            automerge.applySplicesAt(base, baseText, splices)

        fun remoteEdit(newText: String): List<TextSplice> {
            val other = AutomergeTextDocument.fromDocument(automerge.document.fork())
            other.applyLocalText(newText)
            return automerge.merge(other)
        }
    }

    private val doc = Doc("# Page\n\n")
    private val controller = EditorTextController(editModeActive = { true }).also {
        it.bind(doc)
        it.revealAllowed = true
    }

    private val frames = RevealFrames(controller)

    @AfterTest
    fun close() = frames.close()

    private fun advance(ms: Long) = frames.advance(ms)

    private val hidden get() = controller.editorState.rangeStyles.sumOf { it.end - it.start }

    /** where the agent writes next, the start of its hidden text, or null when it shows everything; for one place */
    private val head get() = controller.editorState.rangeStyles.minOfOrNull { it.start }

    private fun agentWrites(insert: String, at: Int = doc.text.length, clientId: String = "agent-1") {
        val text = doc.text
        val splices = doc.remoteEdit(text.substring(0, at) + insert + text.substring(at))
        controller.onRemoteChange(doc.text, splices, "markus", listOf(AgentReveal(clientId, "i-$clientId", listOf(at until at + insert.length))))
        frames.sync()
    }

    private val chunks = listOf(
        0L to "The radiators in the ", 400L to "flat are old seventies models, ", 900L to "and the valves stick ",
        1100L to "when they have not moved ", 2600L to "for a while. To make it ", 2800L to "worse, the thermostat heads ",
        3000L to "are mounted upside down.",
    )

    @Test
    fun `chunks that come while the last one is typed out join it, and nothing shows at once`() {
        var time = 0L
        var lastHead = 0
        for ((at, insert) in chunks) {
            advance(at - time)
            time = at
            val before = hidden
            agentWrites(insert)
            assertEquals(before + insert.length, hidden, "a chunk at $at ms showed text at once")
            head?.let { assertTrue(it >= lastHead, "the head went back at $at ms"); lastHead = it }
        }
        advance(10_000)
        assertEquals(0, hidden)
    }

    @Test
    fun `the head types 60 characters a second, and faster only when the queue would take longer than 4 seconds`() {
        agentWrites("x".repeat(30))
        advance(250)
        // 15 at 60 a second, give or take the fraction carried over
        assertTrue(30 - hidden in 14..15, "${30 - hidden} characters in 250 ms")

        // 330 more: the queue of 345 would take 5.75 s at 60, so it goes at 345 / 4 s
        agentWrites("y".repeat(330))
        val queued = hidden
        advance(1_000)
        assertTrue(queued - hidden in 80..90, "${queued - hidden} characters in a second")
    }

    @Test
    fun `the typing of the user at the edge of the queue still shows it`() {
        agentWrites("The radiators in the ")
        advance(100)
        controller.select(doc.text.length, doc.text.length)
        controller.editorState.replaceSelection("!")
        assertEquals(0, hidden)
    }

    @Test
    fun `another user writing at the edge of the queue still shows it`() {
        agentWrites("The radiators in the ")
        advance(100)
        val text = doc.text
        val splices = doc.remoteEdit(text + "?")
        controller.onRemoteChange(doc.text, splices, "jane")
        assertEquals(0, hidden)
    }

    @Test
    fun `a chunk somewhere else is typed out after the one before`() {
        agentWrites("At the end. ")
        advance(60)
        agentWrites("At the start. ", at = 0)
        // the rest of the first chunk comes first: the new one is still hidden as a whole
        advance(100)
        val start = "At the start. ".length
        assertTrue(controller.editorState.rangeStyles.any { it.start == 0 && it.end == start }, "the new chunk was typed out first")
        assertTrue(hidden > start, "the first chunk was shown at once")
        advance(10_000)
        assertEquals(0, hidden)
    }

    @Test
    fun `two agents type out their chunks side by side`() {
        agentWrites("First agent writes here. ", clientId = "agent-1")
        agentWrites("Second agent too. ", clientId = "agent-2")
        advance(300)
        agentWrites("More of the first. ", at = doc.text.indexOf("Second"), clientId = "agent-1")
        assertTrue(hidden > 0)
        advance(10_000)
        assertEquals(0, hidden)
    }
}
