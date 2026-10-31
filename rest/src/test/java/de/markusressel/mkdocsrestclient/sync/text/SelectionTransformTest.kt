package de.markusressel.mkdocsrestclient.sync.text

import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionTransformTest {

    @Test
    fun `insert before the position moves it`() {
        assertEquals(8, transformIndex(5, listOf(TextSplice(0, 0, "abc"))))
    }

    @Test
    fun `insert after or at the position does not move it`() {
        assertEquals(5, transformIndex(5, listOf(TextSplice(5, 0, "abc"))))
        assertEquals(5, transformIndex(5, listOf(TextSplice(9, 0, "abc"))))
    }

    @Test
    fun `delete before the position moves it back`() {
        assertEquals(2, transformIndex(5, listOf(TextSplice(0, 3, ""))))
    }

    @Test
    fun `position inside a deleted range moves to its start`() {
        assertEquals(2, transformIndex(4, listOf(TextSplice(2, 5, "xy"))))
    }

    @Test
    fun `splices are applied in order`() {
        val splices = listOf(TextSplice(0, 0, "ab"), TextSplice(10, 1, ""))
        assertEquals(7, transformIndex(5, splices))
        assertEquals(11, transformIndex(10, splices))
    }
}
