package de.markusressel.mkdocseditor.auth

import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.OkHttpAuthApi
import de.markusressel.mkdocseditor.auth.discovery.DiscoveryResult
import de.markusressel.mkdocseditor.auth.discovery.ServerDiscovery
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import de.markusressel.mkdocseditor.auth.link.ConnectLinkParser
import de.markusressel.mkdocseditor.auth.link.ConnectLinkResult
import de.markusressel.mkdocseditor.auth.link.ConnectPayload
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Device pairing against a running backend: the "web" side creates a connect link with its
 * session, the "phone" discovers the server and redeems the link. Skipped unless these
 * Gradle properties are set:
 *
 * - `-Pmkdocs.backend.url=http://127.0.0.1:7413`
 * - `-Pmkdocs.backend.users=alice:password` (the first user; no TOTP)
 *
 * With `server.auth.clientUrl` set the link comes as a URL and goes through the parser; the
 * backend should run with `server.auth.rateLimits.disabled: true`.
 */
class LivePairingTest {

    private val baseUrl = System.getProperty("mkdocs.backend.url")?.trimEnd('/')?.plus("/")
    private val user = System.getProperty("mkdocs.backend.users")?.split(",")?.firstOrNull()?.split(":", limit = 2)

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()

    @BeforeEach
    fun requireBackend() {
        assumeTrue(baseUrl != null && user?.size == 2, "set -Pmkdocs.backend.url and -Pmkdocs.backend.users")
    }

    @Test
    fun `discover the server, pair with a connect link once`(): Unit = runBlocking {
        val (username, password) = user!!
        val backend = BackendRef(1, baseUrl!!)

        // the phone finds the backend from its address
        val found = assertIs<DiscoveryResult.Found>(ServerDiscovery(http) { OkHttpAuthApi(it, http) }.discover(baseUrl)).server
        assertEquals(baseUrl, found.backendUrl)
        assertTrue("device-link" in found.info.loginMethods, "server info: ${found.info}")

        // the web client: a fresh login counts as step-up
        val web = AuthRepository(InMemoryTokenStore(), { OkHttpAuthApi(it, http) }, deviceName = "LivePairingTest web")
        assertIs<LoginOutcome.SignedIn>(web.login(backend, username, password))
        val created = web.authenticated(backend) { createDeviceLink(it, "LivePairingTest phone") }
        assertTrue(created.token.startsWith("mdd_"))
        val payload = created.connectUrl?.let { url ->
            val parsed = assertIs<ConnectLinkResult.Valid>(ConnectLinkParser.parse(url)).payload
            assertEquals(created.token, parsed.token)
            assertEquals(username.lowercase(), parsed.username.lowercase())
            parsed
        } ?: ConnectPayload(baseUrl, username, created.token, created.expiresAt)

        // the phone redeems it and has a session of its own
        val phoneStore = InMemoryTokenStore()
        val phone = AuthRepository(phoneStore, { OkHttpAuthApi(it, http) }, deviceName = "LivePairingTest phone")
        assertIs<LoginOutcome.SignedIn>(phone.redeemDeviceLink(backend, payload))
        val sessions = phone.authenticated(backend) { sessions(it) }
        assertEquals("LivePairingTest phone", sessions.single { it.current }.deviceName)

        // a new session may register a passkey without step-up (docs/auth.md); just begin it
        phone.authenticated(backend) { beginAddPasskey(it) }

        // single use
        val again = assertFailsWith<AuthApiException> { AuthRepository(InMemoryTokenStore(), { OkHttpAuthApi(it, http) }, "x").redeemDeviceLink(backend, payload) }
        assertEquals(AuthErrorCode.INVALID_LINK, again.code)

        assertNotNull(phoneStore.sessions[1])
        phone.signOut(backend)
        web.signOut(backend)
    }
}
