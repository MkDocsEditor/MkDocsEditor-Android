package de.markusressel.mkdocseditor.auth.link

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AuthLinkParserTest {

    private val token = "mdl_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8S9t0u-_"

    @Test
    fun `parses web client links`() {
        assertEquals(AuthLink(token, LinkKind.Invite), AuthLinkParser.parse("https://edit.wiki.example.com/invite#$token"))
        assertEquals(AuthLink(token, LinkKind.Setup), AuthLinkParser.parse("https://edit.wiki.example.com/setup/#$token"))
        assertEquals(AuthLink(token, LinkKind.Reset), AuthLinkParser.parse("  http://localhost:5173/reset#$token\n"))
    }

    @Test
    fun `parses links of a web client under a path prefix`() {
        assertEquals(LinkKind.Invite, AuthLinkParser.parse("https://example.com/wiki/editor/invite#$token")?.kind)
    }

    @Test
    fun `unknown path keeps the token without kind`() {
        assertEquals(AuthLink(token, null), AuthLinkParser.parse("https://example.com/something#$token"))
    }

    @Test
    fun `parses the custom scheme`() {
        assertEquals(AuthLink(token, LinkKind.Invite), AuthLinkParser.parse("mkdocseditor://invite#$token"))
        assertEquals(AuthLink(token, LinkKind.Reset), AuthLinkParser.parse("mkdocseditor://reset/#$token"))
    }

    @Test
    fun `parses a bare token`() {
        assertEquals(AuthLink(token, null), AuthLinkParser.parse(token))
    }

    @Test
    fun `rejects text without a valid token`() {
        assertNull(AuthLinkParser.parse(""))
        assertNull(AuthLinkParser.parse("https://edit.wiki.example.com/invite"))
        assertNull(AuthLinkParser.parse("https://edit.wiki.example.com/invite#"))
        assertNull(AuthLinkParser.parse("https://edit.wiki.example.com/invite#mds_sessiontokennotalink1234567890"))
        assertNull(AuthLinkParser.parse("https://edit.wiki.example.com/invite#mdl_short"))
        assertNull(AuthLinkParser.parse("ftp://example.com/invite#$token"))
        assertNull(AuthLinkParser.parse("mdl_with spaces in it 1234567890"))
        assertNull(AuthLinkParser.parse("not a link"))
    }

    @Test
    fun `does not print the token`() {
        assertFalse(AuthLink(token, LinkKind.Invite).toString().contains(token))
    }
}
