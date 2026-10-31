package de.markusressel.mkdocseditor.auth.link

import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectLinkParserTest {

    private val token = "mdd_" + "A1b2_C3d-".repeat(5).take(43)

    private fun payload(json: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray())

    private fun validJson(extra: String = "") =
        """{"v":1,"backendUrl":"https://backend.wiki.example.com","username":"markus","token":"$token","expiresAt":"2026-11-01T12:05:00Z"$extra}"""

    @Test
    fun `web client link`() {
        val result = assertIs<ConnectLinkResult.Valid>(ConnectLinkParser.parse("https://edit.wiki.example.com/connect#${payload(validJson())}"))
        assertEquals("https://backend.wiki.example.com/", result.payload.backendUrl)
        assertEquals("markus", result.payload.username)
        assertEquals(token, result.payload.token)
        assertEquals(Instant.parse("2026-11-01T12:05:00Z"), result.payload.expiresAt)
    }

    @Test
    fun `custom scheme, path prefix and unknown fields`() {
        assertIs<ConnectLinkResult.Valid>(ConnectLinkParser.parse("mkdocseditor://connect#${payload(validJson())}"))
        assertIs<ConnectLinkResult.Valid>(ConnectLinkParser.parse("https://example.com/editor/connect/#${payload(validJson(""","future":true"""))}"))
    }

    @Test
    fun `other versions need an app update`() {
        val result = ConnectLinkParser.parse("https://e.example.com/connect#${payload(validJson().replace("\"v\":1", "\"v\":2"))}")
        assertEquals(ConnectLinkResult.UnsupportedVersion(2), result)
    }

    @Test
    fun `broken payloads are invalid, other links are not connect links`() {
        assertEquals(ConnectLinkResult.Invalid, ConnectLinkParser.parse("https://e.example.com/connect#not*base64"))
        assertEquals(ConnectLinkResult.Invalid, ConnectLinkParser.parse("https://e.example.com/connect#${payload("[1,2]")}"))
        assertEquals(ConnectLinkResult.Invalid, ConnectLinkParser.parse("https://e.example.com/connect#${payload(validJson().replace(token, "mdl_short"))}"))
        assertEquals(
            ConnectLinkResult.Invalid,
            ConnectLinkParser.parse("https://e.example.com/connect#${payload(validJson().replace("https://backend.wiki.example.com", "ftp://x"))}")
        )
        assertNull(ConnectLinkParser.parse("https://e.example.com/invite#mdl_abcdefghijklmnopqrstuvwxyz"))
        assertNull(ConnectLinkParser.parse("https://e.example.com/connect"))
        assertNull(ConnectLinkParser.parse("just text"))
        assertFalse(ConnectLinkParser.isConnectLink("mkdocseditor://invite#x"))
        assertTrue(ConnectLinkParser.isConnectLink("mkdocseditor://connect#x"))
    }

    @Test
    fun `expiry and a token hidden from logs`() {
        val payload = (ConnectLinkParser.parse("mkdocseditor://connect#${payload(validJson())}") as ConnectLinkResult.Valid).payload
        assertFalse(payload.isExpired(Instant.parse("2026-11-01T12:04:59Z")))
        assertTrue(payload.isExpired(Instant.parse("2026-11-01T12:05:00Z")))
        assertFalse(token in payload.toString())
    }
}
