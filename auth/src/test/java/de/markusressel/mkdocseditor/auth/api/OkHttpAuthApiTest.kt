package de.markusressel.mkdocseditor.auth.api

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OkHttpAuthApiTest {

    private val server = MockWebServer()
    private lateinit var api: OkHttpAuthApi

    @Before
    fun setUp() {
        server.start()
        api = OkHttpAuthApi(server.url("/").toString(), OkHttpClient())
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        server.enqueue(
            MockResponse.Builder().code(code).body(body).apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
            }.build()
        )
    }

    private fun RecordedRequest.bodyText(): String = body?.utf8() ?: ""

    @Test
    fun `login sends a bearer session request and parses the session`() = runTest {
        respond(
            200, """
            {"status": "ok",
             "user": {"id": "k3x8", "username": "markus", "displayName": "Markus", "role": "admin", "color": "#e8590c", "secondFactorSetupRequired": false},
             "session": {"id": "p1q2", "type": "bearer", "deviceName": "Pixel 8", "createdAt": "2026-10-31T20:00:00Z", "expiresAt": "2027-01-29T20:00:00Z", "idleExpiresAt": "2026-11-30T20:00:00+00:00"},
             "token": "mds_abc"}
            """.trimIndent()
        )

        val response = api.login("markus", "correct horse", "Pixel 8")

        assertEquals("ok", response.status)
        assertEquals("mds_abc", response.token)
        assertEquals("markus", response.user?.username)
        assertEquals(Instant.parse("2026-11-30T20:00:00Z"), response.session?.idleExpiresAt)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/auth/login/", request.url.encodedPath)
        assertEquals("MkDocsEditor-Android", request.headers["X-Requested-With"])
        assertNull(request.headers["Authorization"])
        val body = request.bodyText()
        assertTrue(body.contains("\"sessionType\":\"bearer\""), body)
        assertTrue(body.contains("\"deviceName\":\"Pixel 8\""), body)
    }

    @Test
    fun `second factor required is a normal answer`() = runTest {
        respond(
            200,
            """{"status": "second-factor-required", "challenge": "mdc_x", "methods": ["totp", "recovery-code", "passkey"], "expiresAt": "2026-10-31T20:05:00Z"}"""
        )
        val response = api.login("markus", "pw", "Pixel")
        assertEquals("second-factor-required", response.status)
        assertEquals("mdc_x", response.challenge)
        assertEquals(listOf("totp", "recovery-code", "passkey"), response.methods)
        assertNull(response.token)
    }

    @Test
    fun `second factor leaves out the unused field`() = runTest {
        respond(200, """{"status": "ok", "token": "mds_t"}""")
        api.secondFactor(challenge = "mdc_x", recoveryCode = "abcd-efgh-jkmn", deviceName = "Pixel")
        val body = server.takeRequest().bodyText()
        assertTrue(body.contains("\"recoveryCode\":\"abcd-efgh-jkmn\""))
        assertTrue(!body.contains("\"code\""), body)
    }

    @Test
    fun `errors carry code, details and retry after`() = runTest {
        respond(
            401,
            """{"name": "Unauthorized", "code": "invalid-second-factor", "message": "wrong code", "details": {"attemptsLeft": 3}}"""
        )
        val error = assertFailsWith<AuthApiException> {
            api.secondFactor(challenge = "mdc_x", code = "000000", deviceName = "Pixel")
        }
        assertEquals(401, error.status)
        assertEquals(AuthErrorCode.INVALID_SECOND_FACTOR, error.code)
        assertEquals(3, (error.details["attemptsLeft"] as Number).toInt())
        assertTrue(!error.isUnauthenticated)

        respond(
            429,
            """{"name": "Too Many Requests", "code": "rate-limited", "message": "slow down", "details": {"retryAfter": 12}}""",
            mapOf("Retry-After" to "12"),
        )
        val limited = assertFailsWith<AuthApiException> { api.login("a", "b", "c") }
        assertEquals(12, limited.retryAfterSeconds)
    }

    @Test
    fun `an error without a json body still is an AuthApiException`() = runTest {
        respond(502, "Bad Gateway")
        val error = assertFailsWith<AuthApiException> { api.status() }
        assertEquals(502, error.status)
        assertNull(error.code)
    }

    @Test
    fun `unauthenticated is detected`() = runTest {
        respond(401, """{"name": "Unauthorized", "code": "unauthenticated", "message": "session expired"}""")
        val error = assertFailsWith<AuthApiException> { api.whoAmI("mds_old") }
        assertTrue(error.isUnauthenticated)
        assertEquals("Bearer mds_old", server.takeRequest().headers["Authorization"])
    }

    @Test
    fun `passkey options are passed through unchanged`() = runTest {
        val publicKey =
            """{"challenge":"Y2hh","rpId":"wiki.example.com","timeout":300000,"userVerification":"required","allowCredentials":[]}"""
        respond(200, """{"ceremonyId": "cer1", "publicKey": $publicKey}""")

        val ceremony = api.beginPasskeyLogin()

        assertEquals("cer1", ceremony.ceremonyId)
        assertEquals(publicKey, ceremony.publicKey)
        assertEquals("{}", server.takeRequest().bodyText())
    }

    @Test
    fun `passkey credential is embedded as json`() = runTest {
        respond(200, """{"status": "ok", "token": "mds_t"}""")
        val credential = """{"id":"abc","rawId":"abc","type":"public-key","response":{"clientDataJSON":"e30"}}"""

        api.finishPasskeyLogin("cer1", credential, "Pixel")

        val body = server.takeRequest().bodyText()
        assertTrue(body.contains("\"credential\":$credential"), body)
    }

    @Test
    fun `status defaults missing fields`() = runTest {
        respond(200, """{"setupRequired": true, "passkeys": {"enabled": true, "rpId": "wiki.example.com"}}""")
        val status = api.status()
        assertTrue(status.setupRequired)
        assertTrue(status.passkeys.enabled)
        assertEquals(10, status.passwordPolicy.minLength)
    }

    @Test
    fun `account endpoints`() = runTest {
        respond(200, """{"secret": "JBSW", "otpauthUri": "otpauth://totp/MkDocsEditor:markus?secret=JBSW", "expiresAt": "2026-10-31T20:10:00Z"}""")
        assertEquals("JBSW", api.beginTotp("mds_t").secret)

        respond(200, """{"recoveryCodes": ["a", "b"]}""")
        assertEquals(listOf("a", "b"), api.confirmTotp("mds_t", "123456"))

        respond(200, """[{"id": "s1", "deviceName": "Pixel", "current": true}, {"id": "s2"}]""")
        val sessions = api.sessions("mds_t")
        assertEquals(2, sessions.size)
        assertTrue(sessions.first().current)

        respond(200, """{"revoked": 3}""")
        assertEquals(3, api.revokeOtherSessions("mds_t"))

        respond(204)
        api.deletePasskey("mds_t", "f7g8")

        respond(204)
        api.changePassword("mds_t", currentPassword = null, newPassword = "a new long passphrase")

        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        server.takeRequest()
        val delete = server.takeRequest()
        assertEquals("DELETE", delete.method)
        assertEquals("/account/passkeys/f7g8/", delete.url.encodedPath)
        val password = server.takeRequest()
        assertEquals("PUT", password.method)
        assertEquals("""{"newPassword":"a new long passphrase"}""", password.bodyText())
    }

    @Test
    fun `passkeys with the provider, unused ones without lastUsedAt`() = runTest {
        respond(
            200,
            """[{"id": "p1", "name": "Phone", "createdAt": "2026-10-31T20:00:00Z", "lastUsedAt": null, "backedUp": true,
                "transports": ["internal", "hybrid"], "provider": "Bitwarden"}, {"id": "p2", "name": "Key"}]"""
        )
        val (phone, key) = api.passkeys("mds_t")
        assertEquals("Bitwarden", phone.provider)
        assertNull(phone.lastUsedAt)
        assertTrue(phone.backedUp)
        assertNull(key.provider)
    }

    @Test
    fun `server info and device link redeem`() = runTest {
        respond(200, """{"name": "Wiki", "protocolVersion": 1, "backendUrl": null, "loginMethods": ["password", "device-link"], "future": 1}""")
        val info = api.serverInfo()
        assertEquals("Wiki", info.name)
        assertNull(info.backendUrl)
        assertEquals(listOf("password", "device-link"), info.loginMethods)

        respond(200, """{"status": "ok", "user": {"id": "u", "username": "markus", "displayName": "Markus", "role": "admin"}, "token": "mds_x"}""")
        assertEquals("mds_x", api.redeemDeviceLink("mdd_token", "markus", "Pixel 8").token)

        assertEquals("/server-info/", server.takeRequest().url.encodedPath)
        val redeem = server.takeRequest()
        assertEquals("/auth/device-link/redeem/", redeem.url.encodedPath)
        assertEquals("MkDocsEditor-Android", redeem.headers["X-Requested-With"])
        assertEquals("""{"token":"mdd_token","username":"markus","deviceName":"Pixel 8","sessionType":"bearer"}""", redeem.bodyText())
    }

    @Test
    fun `base url with a path prefix`() = runTest {
        val prefixed = OkHttpAuthApi(server.url("/api").toString(), OkHttpClient())
        respond(200, "{}")
        prefixed.status()
        assertEquals("/api/auth/status/", server.takeRequest().url.encodedPath)
    }
}
