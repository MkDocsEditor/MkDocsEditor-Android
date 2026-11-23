package de.markusressel.mkdocseditor.feature.editor.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentRevealsTest {

    private val reveals = AgentReveals()
    private val text = "x".repeat(400)

    @Test
    fun `text is typed out at 60 characters a second, fractions carry over`() {
        reveals.start("a1", "i1", listOf(10 until 40))

        // 30 ms at 60/s are 1.8 characters
        assertEquals(listOf(AgentReveals.Revealed(10, 11, "agent:a1")), reveals.step(30, text))
        // 0.8 left over plus 1.8 are 2.6
        assertEquals(listOf(AgentReveals.Revealed(11, 13, "agent:a1")), reveals.step(30, text))
        assertEquals(listOf(13 until 40), reveals.hiddenRanges)
        assertEquals(mapOf("a1" to 13), reveals.heads)
    }

    @Test
    fun `no reveal takes longer than 4 seconds, and a long change shows at once`() {
        // 300 characters take 4 seconds, at 75 a second
        reveals.start("a1", "i1", listOf(0 until 200, 300 until 400))
        reveals.step(2_000, text)
        assertEquals(listOf(150 until 200, 300 until 400), reveals.hiddenRanges)
        reveals.step(2_000, text)
        assertTrue(reveals.isEmpty)

        assertFalse(reveals.start("a1", "i1", listOf(0 until AgentReveals.MAX_LENGTH + 1)))
        assertTrue(reveals.isEmpty)
    }

    @Test
    fun `an emoji is never cut in half`() {
        val emojis = "😀".repeat(20)
        reveals.start("a1", "i1", listOf(0 until emojis.length))

        // 1 unit at 60/s would end in the middle of the first emoji
        assertEquals(listOf(AgentReveals.Revealed(0, 2, "agent:a1")), reveals.step(17, emojis))
    }

    @Test
    fun `an edit inside or at the edge of a hidden range shows that range, others move along`() {
        reveals.start("a1", "i1", listOf(10 until 20, 30 until 40))

        assertFalse(reveals.onEdit(start = 0, removed = 0, inserted = 2))
        assertEquals(listOf(12 until 22, 32 until 42), reveals.hiddenRanges)

        assertTrue(reveals.onEdit(start = 22, removed = 0, inserted = 1))
        assertEquals(listOf(33 until 43), reveals.hiddenRanges)
    }

    @Test
    fun `a tap on the text being typed out shows all of that agent, elsewhere it does not`() {
        reveals.start("a1", "i1", listOf(10 until 20, 30 until 40))
        reveals.start("a2", "i2", listOf(50 until 60))

        assertFalse(reveals.onTap(25))
        assertTrue(reveals.onTap(10))
        assertEquals(listOf(50 until 60), reveals.hiddenRanges)
    }

    @Test
    fun `a new change of the same agent finishes its reveal, other agents go on in parallel`() {
        reveals.start("a1", "i1", listOf(10 until 20))
        reveals.start("a2", "i2", listOf(50 until 60))

        reveals.start("a1", "i1", listOf(70 until 80))

        assertEquals(listOf(50 until 60, 70 until 80), reveals.hiddenRanges)
        assertEquals(2, reveals.step(100, text).map { it.tag }.distinct().size)
    }

    @Test
    fun `a folded part shows as a whole and counts as one character`() {
        reveals.start("a1", "i1", listOf(10 until 40))
        // 20 until 35 is folded
        val folded = { position: Int -> if (position in 20 until 35) 35 else null }

        // 9.6 characters
        reveals.step(160, text, folded)
        assertEquals(listOf(19 until 40), reveals.hiddenRanges)

        // 0.6 left over plus 1.8 are 2.4: one character, then the whole fold
        assertEquals(listOf(AgentReveals.Revealed(19, 35, "agent:a1")), reveals.step(30, text, folded))
        assertEquals(listOf(35 until 40), reveals.hiddenRanges)
    }

    @Test
    fun `a fold that reaches beyond the hidden text shows only the hidden text`() {
        reveals.start("a1", "i1", listOf(10 until 20))

        assertEquals(listOf(AgentReveals.Revealed(10, 20, "agent:a1")), reveals.step(17, text) { 50 })
        assertTrue(reveals.isEmpty)
    }
}
