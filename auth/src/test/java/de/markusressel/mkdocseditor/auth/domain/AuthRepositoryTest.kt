package de.markusressel.mkdocseditor.auth.domain

import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.LoginResponse
import de.markusressel.mkdocseditor.auth.link.AuthLink
import de.markusressel.mkdocseditor.auth.link.LinkKind
import de.markusressel.mkdocseditor.auth.okResponse
import de.markusressel.mkdocseditor.auth.token.AuthSession
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthRepositoryTest {

    private val backend = BackendRef(id = 7, baseUrl = "https://backend.example.com/")
    private val api = FakeAuthApi()
    private val store = InMemoryTokenStore()
    private var now = Instant.parse("2026-11-01T00:00:00Z")
    private val repository = AuthRepository(store, { api }, deviceName = "Pixel 8", clock = { now })

    @Test
    fun `password login without second factor stores the session`() = runTest {
        api.onLogin = { _, _ -> okResponse(token = "mds_abc") }

        val outcome = repository.login(backend, " markus ", "pw")

        assertIs<LoginOutcome.SignedIn>(outcome)
        assertEquals("mds_abc", repository.token(7))
        assertEquals("markus", store.sessions[7]?.username)
        assertEquals("login:markus:Pixel 8", api.calls.single())
    }

    @Test
    fun `second factor flow`() = runTest {
        api.onLogin = { _, _ ->
            LoginResponse(status = "second-factor-required", challenge = "mdc_1", methods = listOf("totp", "recovery-code", "unknown"))
        }
        val outcome = repository.login(backend, "markus", "pw")
        assertIs<LoginOutcome.SecondFactorRequired>(outcome)
        assertEquals(setOf(SecondFactorMethod.Totp, SecondFactorMethod.RecoveryCode), outcome.methods)
        assertNull(repository.token(7))

        var sentCode: String? = null
        api.onSecondFactor = { challenge, code, _ ->
            assertEquals("mdc_1", challenge)
            sentCode = code
            okResponse()
        }
        assertIs<LoginOutcome.SignedIn>(repository.submitTotpCode(backend, outcome.challenge, "123 456"))
        assertEquals("123456", sentCode)
        assertEquals("mds_token", repository.token(7))
    }

    @Test
    fun `recovery code is sent as recovery code`() = runTest {
        var recovery: String? = null
        api.onSecondFactor = { _, code, recoveryCode ->
            assertNull(code)
            recovery = recoveryCode
            okResponse()
        }
        repository.submitRecoveryCode(backend, "mdc_1", " abcd-efgh-jkmn ")
        assertEquals("abcd-efgh-jkmn", recovery)
    }

    @Test
    fun `restricted session is reported`() = runTest {
        api.onLogin = { _, _ -> okResponse(secondFactorSetupRequired = true) }
        val outcome = repository.login(backend, "markus", "pw")
        assertIs<LoginOutcome.SignedIn>(outcome)
        assertTrue(outcome.secondFactorSetupRequired)
    }

    @Test
    fun `a session answer without token is an error`() = runTest {
        api.onLogin = { _, _ -> okResponse().copy(token = null) }
        assertFailsWith<IOException> { repository.login(backend, "markus", "pw") }
        assertNull(repository.token(7))
    }

    @Test
    fun `wrong password keeps the user signed out`() = runTest {
        api.onLogin = { _, _ -> throw AuthApiException(401, AuthErrorCode.INVALID_CREDENTIALS, "nope") }
        val error = assertFailsWith<AuthApiException> { repository.login(backend, "markus", "pw") }
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, error.code)
        assertTrue(store.sessions.isEmpty())
    }

    @Test
    fun `expired session is dropped`() {
        store.put(7, AuthSession("mds_old", Instant.parse("2026-10-31T23:59:59Z"), "markus"))
        assertNull(repository.token(7))
        assertTrue(store.sessions.isEmpty())
    }

    @Test
    fun `unauthenticated answer drops the session and notifies`() = runTest(UnconfinedTestDispatcher()) {
        store.put(7, AuthSession("mds_t", null, "markus"))
        api.onAccount = { throw AuthApiException(401, AuthErrorCode.UNAUTHENTICATED, "expired") }

        val ended = mutableListOf<Long>()
        val job = launch { repository.sessionEnded.collect { ended += it } }

        assertFailsWith<AuthApiException> { repository.authenticated(backend) { account(it) } }

        assertNull(repository.token(7))
        assertEquals(listOf(7L), ended)
        job.cancel()
    }

    @Test
    fun `other errors keep the session`() = runTest {
        store.put(7, AuthSession("mds_t", null, "markus"))
        api.onAccount = { throw AuthApiException(403, AuthErrorCode.REAUTH_REQUIRED, "step up") }
        assertFailsWith<AuthApiException> { repository.authenticated(backend) { account(it) } }
        assertEquals("mds_t", repository.token(7))
    }

    @Test
    fun `authenticated without session fails fast`() = runTest {
        assertFailsWith<NotSignedInException> { repository.authenticated(backend) { account(it) } }
        assertTrue(api.calls.isEmpty())
    }

    @Test
    fun `sign out forgets the session even when the server is unreachable`() = runTest {
        store.put(7, AuthSession("mds_t", null, "markus"))
        api.onLogout = { throw IOException("offline") }
        repository.signOut(backend)
        assertNull(repository.token(7))
        assertEquals("logout:mds_t", api.calls.single())
    }

    @Test
    fun `invite redeem creates the session`() = runTest {
        api.onRedeem = { _, username, _ -> okResponse(username = username!!) }
        val link = AuthLink("mdl_x", LinkKind.Invite)
        val outcome = repository.redeemLinkWithPassword(backend, link, " anna ", " ", "a long password")
        assertIs<LoginOutcome.SignedIn>(outcome)
        assertEquals("redeem:anna:null", api.calls.single())
        assertEquals("anna", store.sessions[7]?.username)
    }

    @Test
    fun `passkey login`() = runTest {
        api.onFinishPasskeyLogin = { ceremonyId, credential ->
            assertEquals("cer", ceremonyId)
            assertEquals("{\"id\":\"c\"}", credential)
            okResponse()
        }
        val ceremony = repository.beginPasskeyLogin(backend)
        repository.finishPasskeyLogin(backend, ceremony.ceremonyId, "{\"id\":\"c\"}")
        assertEquals(listOf("beginPasskeyLogin:null", "finishPasskeyLogin:cer"), api.calls)
        assertEquals("mds_token", repository.token(7))
    }

    @Test
    fun `session changes are emitted`() = runTest(UnconfinedTestDispatcher()) {
        api.onLogin = { _, _ -> okResponse() }
        val changes = mutableListOf<Long>()
        val job = launch { repository.sessionChanges.collect { changes += it } }
        repository.login(backend, "markus", "pw")
        repository.forget(7)
        assertEquals(listOf(7L, 7L), changes)
        job.cancel()
    }
}
