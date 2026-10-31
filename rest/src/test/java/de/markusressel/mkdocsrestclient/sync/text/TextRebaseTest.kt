package de.markusressel.mkdocsrestclient.sync.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TextRebaseTest {

    private fun rebase(base: String, local: String, server: String) =
        server.applySplices(rebaseSplices(base, local, server))

    @Test
    fun `local and server edits in different places are both kept`() {
        assertEquals("ALPHA\nbeta\ngamma\n", rebase("alpha\nbeta\n", "alpha\nbeta\ngamma\n", "ALPHA\nbeta\n"))
    }

    @Test
    fun `no local edits leave the server text alone`() {
        assertEquals(emptyList(), rebaseSplices("same", "same", "other"))
    }

    @Test
    fun `local insert moves with server inserts before it`() {
        assertEquals("server: hello brave world", rebase("hello world", "hello brave world", "server: hello world"))
    }

    @Test
    fun `local delete of text the server changed does not delete the server text around it`() {
        assertEquals("a  c", rebase("a b c", "a  c", "a b c"))
        assertEquals("X c", rebase("a b c", "a c", "X b c"))
    }
}
