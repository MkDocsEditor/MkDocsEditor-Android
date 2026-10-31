package de.markusressel.mkdocsrestclient.sync.text

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextDiffTest {

    @Test
    fun `equal texts produce no splices`() {
        assertEquals(emptyList(), TextDiff.diff("same", "same"))
    }

    @Test
    fun `typing a character is a single insert`() {
        assertEquals(listOf(TextSplice(5, 0, "!")), TextDiff.diff("hello world", "hello! world"))
    }

    @Test
    fun `deleting a character is a single delete`() {
        assertEquals(listOf(TextSplice(4, 1, "")), TextDiff.diff("hello", "hell"))
    }

    @Test
    fun `replacing a word is a single splice`() {
        assertEquals(listOf(TextSplice(6, 5, "there")), TextDiff.diff("hello world", "hello there"))
    }

    @Test
    fun `inserting into an empty text and clearing a text`() {
        assertEquals(listOf(TextSplice(0, 0, "abc")), TextDiff.diff("", "abc"))
        assertEquals(listOf(TextSplice(0, 3, "")), TextDiff.diff("abc", ""))
    }

    @Test
    fun `wrapping a selection keeps the selected text`() {
        val splices = TextDiff.diff("make this bold", "make **this** bold")
        assertEquals(listOf(TextSplice(5, 0, "**"), TextSplice(11, 0, "**")), splices)
    }

    @Test
    fun `repeated characters are diffed at the edit position`() {
        // ambiguous: the inserted "a" could be anywhere in the run
        val splices = TextDiff.diff("aaaa", "aaaaa")
        assertEquals("aaaaa", "aaaa".applySplices(splices))
        assertEquals(1, splices.size)
        assertEquals(1, splices.single().insert.length)
    }

    @Test
    fun `surrogate pairs are never split`() {
        // both emojis share the high surrogate
        val old = "x😀y"
        val new = "x😁y"
        val splices = TextDiff.diff(old, new)
        assertEquals(listOf(TextSplice(1, 2, "😁")), splices)
    }

    @Test
    fun `random edits always reproduce the new text`() {
        val random = Random(42)
        val alphabet = "ab \n#*😀ä"
        fun randomText(length: Int) = buildString {
            repeat(length) {
                val start = random.nextInt(alphabet.length)
                val c = alphabet[start]
                if (c.isHighSurrogate()) append(alphabet, start, start + 2)
                else if (c.isLowSurrogate()) append(alphabet, start - 1, start + 1)
                else append(c)
            }
        }

        repeat(500) {
            val old = randomText(random.nextInt(0, 60))
            val new = randomText(random.nextInt(0, 60))
            val splices = TextDiff.diff(old, new)
            assertEquals(new, old.applySplices(splices), "old=<$old> new=<$new> splices=$splices")

            var current = old
            splices.forEach { splice ->
                assertTrue(splice.index == 0 || !current[splice.index - 1].isHighSurrogate(), "split pair in $splices")
                current = splice.applyTo(current)
            }
        }
    }

    @Test
    fun `large rewrites fall back to a single splice`() {
        val random = Random(1)
        val old = String(CharArray(5000) { 'a' + random.nextInt(26) })
        val new = String(CharArray(5000) { 'a' + random.nextInt(26) })
        val splices = TextDiff.diff(old, new)
        assertEquals(new, old.applySplices(splices))
    }
}
