package de.markusressel.mkdocseditor.auth

import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.OkHttpAuthApi
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Session login against a running MkDocsEditor backend (docs/auth.md). Skipped unless these
 * Gradle properties are set:
 *
 * - `-Pmkdocs.backend.url=http://127.0.0.1:7413`
 * - `-Pmkdocs.backend.users=alice:password` (the first user is used; an account without TOTP)
 *
 * The backend should run with `server.auth.rateLimits.disabled: true`, since the test logs
 * in several times from one address. It signs out its own sessions, nothing else changes.
 */
class LiveAuthTest {

    private val baseUrl = System.getProperty("mkdocs.backend.url")?.trimEnd('/')?.plus("/")
    private val user = System.getProperty("mkdocs.backend.users")?.split(",")?.firstOrNull()?.split(":", limit = 2)

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()

    @BeforeEach
    fun requireBackend() {
        assumeTrue(baseUrl != null && user?.size == 2, "set -Pmkdocs.backend.url and -Pmkdocs.backend.users")
    }

    private fun repository(store: InMemoryTokenStore, device: String) =
        AuthRepository(store, { OkHttpAuthApi(it, http) }, deviceName = device)

    @Test
    fun `status says sessions are on`() = runBlocking {
        val status = OkHttpAuthApi(baseUrl!!, http).status()
        assertFalse(status.authDisabled)
        assertTrue(status.passwordPolicy.minLength > 0)
    }

    @Test
    fun `login, sessions and logout`() = runBlocking {
        val (username, password) = user!!
        val backend = BackendRef(1, baseUrl!!)
        val phoneStore = InMemoryTokenStore()
        val tabletStore = InMemoryTokenStore()
        val phone = repository(phoneStore, "LiveAuthTest phone")
        val tablet = repository(tabletStore, "LiveAuthTest tablet")

        val outcome = phone.login(backend, username, password)
        assertIs<LoginOutcome.SignedIn>(outcome, "the test user must not have TOTP")
        val token = assertNotNull(phoneStore.sessions[1]).token
        assertTrue(token.startsWith("mds_"))

        val own = phone.authenticated(backend) { sessions(it) }
        assertEquals("LiveAuthTest phone", own.single { it.current }.deviceName)

        // a second device sees both sessions and signs the first one out
        assertIs<LoginOutcome.SignedIn>(tablet.login(backend, username, password))
        val sessions = tablet.authenticated(backend) { sessions(it) }
        assertTrue(sessions.first().current)
        assertTrue(sessions.any { it.deviceName == "LiveAuthTest phone" })
        assertTrue(tablet.authenticated(backend) { revokeOtherSessions(it) } >= 1)

        // the revoked token is rejected and dropped locally
        val error = assertFailsWith<AuthApiException> { phone.authenticated(backend) { sessions(it) } }
        assertTrue(error.isUnauthenticated)
        assertNull(phoneStore.sessions[1])

        // sign-out revokes the session on the server
        val tabletToken = assertNotNull(tabletStore.sessions[1]).token
        tablet.signOut(backend)
        assertNull(tabletStore.sessions[1])
        val afterLogout = assertFailsWith<AuthApiException> { OkHttpAuthApi(baseUrl, http).sessions(tabletToken) }
        assertEquals(401, afterLogout.status)
    }

    @Test
    fun `wrong password is rejected`() = runBlocking {
        val (username, _) = user!!
        val error = assertFailsWith<AuthApiException> {
            repository(InMemoryTokenStore(), "LiveAuthTest").login(BackendRef(1, baseUrl!!), username, "definitely wrong password")
        }
        assertEquals(401, error.status)
    }
}
