package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlin.test.Test
import kotlin.test.assertEquals

class SplitAgentTextTest {

    private fun String.apply(splices: List<TextSplice>) = splices.fold(this) { text, it ->
        text.substring(0, it.index) + it.insert + text.substring(it.index + it.deleteCount)
    }

    /** the split change gives the same text: the splices first, then the chunks in their order */
    private fun SplitChange.applyTo(before: String) =
        chunks.fold(before.apply(splices)) { text, it -> text.substring(0, it.position) + it.text + text.substring(it.position) }

    @Test
    fun `the text of an agent becomes a chunk, and nothing of it shows at once`() {
        val splices = listOf(TextSplice(6, 0, "brave "))
        val split = splitAgentText("hello brave world", splices, mapOf("agent" to listOf(6 until 12)))

        assertEquals(emptyList(), split.splices)
        assertEquals(listOf(AgentChunk("agent", 6, "brave ")), split.chunks)
    }

    @Test
    fun `changes of people in the same sync show at once, before and after the agent's text`() {
        val before = "one two three"
        // a person deletes "one ", the agent inserts "big " before "three", a person appends "!"
        val splices = listOf(TextSplice(0, 4, ""), TextSplice(4, 0, "big "), TextSplice(13, 0, "!"))
        val after = before.apply(splices)
        assertEquals("two big three!", after)

        val split = splitAgentText(after, splices, mapOf("agent" to listOf(4 until 8)))

        assertEquals(listOf(TextSplice(0, 4, ""), TextSplice(9, 0, "!")), split.splices)
        assertEquals(listOf(AgentChunk("agent", 4, "big ")), split.chunks)
        assertEquals(after, split.applyTo(before))
    }

    @Test
    fun `an insert with text of a person and of an agent keeps the person's part`() {
        val splices = listOf(TextSplice(1, 0, "xxAGENTyy"))
        val after = "a".apply(splices)

        val split = splitAgentText(after, splices, mapOf("agent" to listOf(3 until 8)))

        assertEquals(listOf(TextSplice(1, 0, "xxyy")), split.splices)
        assertEquals(listOf(AgentChunk("agent", 3, "AGENT")), split.chunks)
        assertEquals(after, split.applyTo("a"))
    }

    @Test
    fun `two agents get their own chunks, in the order of the text`() {
        val splices = listOf(TextSplice(2, 0, "second "), TextSplice(0, 0, "first "))
        val before = "a b"
        val after = before.apply(splices)
        assertEquals("first a second b", after)

        val split = splitAgentText(after, splices, mapOf("two" to listOf(8 until 15), "one" to listOf(0 until 6)))

        assertEquals(emptyList(), split.splices)
        assertEquals(listOf(AgentChunk("one", 0, "first "), AgentChunk("two", 8, "second ")), split.chunks)
        assertEquals(after, split.applyTo(before))
    }

    @Test
    fun `ranges that no splice inserted leave the change as it is`() {
        val splices = listOf(TextSplice(0, 0, "x"))

        val split = splitAgentText("xhello", splices, mapOf("agent" to listOf(1 until 3)))

        assertEquals(SplitChange(splices, emptyList()), split)
    }
}
