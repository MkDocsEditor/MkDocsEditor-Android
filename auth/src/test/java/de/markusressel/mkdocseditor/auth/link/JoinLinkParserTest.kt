package de.markusressel.mkdocseditor.auth.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinLinkParserTest {

    @Test
    fun `reads document and sender`() {
        assertEquals(
            JoinLink("https://edit.example.com/", "abc123", "acc1"),
            JoinLinkParser.parse("https://edit.example.com/join/abc123?from=acc1"),
        )
    }

    @Test
    fun `keeps a port and a path prefix in the client url`() {
        assertEquals(
            JoinLink("http://host:8080/wiki/", "abc", null),
            JoinLinkParser.parse("http://HOST:8080/wiki/join/abc"),
        )
    }

    @Test
    fun `a built link reads back the same`() {
        val link = JoinLinkParser.build("https://edit.example.com/", "a b+c/d", "x y")
        assertEquals(JoinLink("https://edit.example.com/", "a b+c/d", "x y"), JoinLinkParser.parse(link))
    }

    @Test
    fun `a link names the instance to follow`() {
        val built = JoinLinkParser.build("https://edit.example.com", "doc", "sender", "0f8a")
        assertEquals("https://edit.example.com/join/doc?from=sender&instance=0f8a", built)
        assertEquals(JoinLink("https://edit.example.com/", "doc", "sender", "0f8a"), JoinLinkParser.parse(built))
    }

    @Test
    fun `a link of an older client has no instance`() {
        assertNull(JoinLinkParser.parse("https://edit.example.com/join/doc?from=sender")?.instance)
    }

    @Test
    fun `other links are no join links`() {
        assertNull(JoinLinkParser.parse("https://edit.example.com/invite#mdl_abc"))
        assertNull(JoinLinkParser.parse("https://edit.example.com/join/"))
        assertNull(JoinLinkParser.parse("mkdocseditor://join/abc"))
        assertNull(JoinLinkParser.parse("not a link"))
    }
}
