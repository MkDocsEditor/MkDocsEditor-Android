package de.markusressel.mkdocseditor.auth

import de.markusressel.mkdocseditor.auth.api.AccountUpdate
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthErrorCode
import de.markusressel.mkdocseditor.auth.api.OkHttpAuthApi
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import de.markusressel.mkdocseditor.auth.domain.SecondFactorMethod
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
 * The account screen's calls against a running backend: profile, password, TOTP with
 * recovery codes, the second factor at login, step-up, and passkeys through a software
 * authenticator that answers like Bitwarden in Credential Manager. Skipped unless these
 * Gradle properties are set:
 *
 * - `-Pmkdocs.backend.url=http://127.0.0.1:7413`
 * - `-Pmkdocs.backend.users=alice:password` (the first user; no TOTP and no passkey yet)
 * - optional `-Pmkdocs.backend.passkeyOrigin=...`, default: the Android origin of the CI
 *   debug build, so `server.auth.webauthn.android` must list that package and fingerprint
 *
 * The backend should run with `server.auth.rateLimits.disabled: true`. The test sets the
 * account back at the end: TOTP off, its passkeys deleted, the old password and name.
 */
class LiveAccountTest {

    private val baseUrl = System.getProperty("mkdocs.backend.url")?.trimEnd('/')?.plus("/")
    private val user = System.getProperty("mkdocs.backend.users")?.split(",")?.firstOrNull()?.split(":", limit = 2)
    private val passkeyOrigin = System.getProperty("mkdocs.backend.passkeyOrigin") ?: CI_DEBUG_ORIGIN

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()

    @BeforeEach
    fun requireBackend() {
        assumeTrue(baseUrl != null && user?.size == 2, "set -Pmkdocs.backend.url and -Pmkdocs.backend.users")
    }

    @Test
    fun `account, TOTP, step-up and passkeys`(): Unit = runBlocking {
        val (username, password) = user!!
        val backend = BackendRef(1, baseUrl!!)
        val store = InMemoryTokenStore()
        val repository = AuthRepository(store, { OkHttpAuthApi(it, http) }, deviceName = "LiveAccountTest")
        suspend fun <T> call(block: suspend de.markusressel.mkdocseditor.auth.api.AuthApi.(String) -> T) =
            repository.authenticated(backend, block)

        assertIs<LoginOutcome.SignedIn>(repository.login(backend, username, password), "the test user must not have TOTP")
        val original = call { account(it) }
        assertEquals(username, original.username)
        assertTrue(original.hasPassword)

        var currentPassword = password
        var totpOn = false
        val authenticator = repository.status(backend).passkeys.rpId?.let { SoftwareAuthenticator(it, passkeyOrigin) }
        try {
            // profile
            val renamed = call { updateAccount(it, AccountUpdate(displayName = "Live Test")) }
            assertEquals("Live Test", renamed.displayName)

            // password: with the current one no step-up is needed
            val newPassword = "$password and more"
            call { changePassword(it, password, newPassword) }
            currentPassword = newPassword
            assertIs<LoginOutcome.SignedIn>(repository.login(backend, username, newPassword))

            // TOTP: a fresh login counts as recent, so enrolment needs no step-up
            val enrollment = call { beginTotp(it) }
            assertTrue(enrollment.otpauthUri.startsWith("otpauth://totp/"))
            assertEquals(enrollment.secret, Totp.secretOf(enrollment.otpauthUri))
            val wrongCode = (0..9).map { "$it".repeat(6) }
                .first { c -> (-1..1).none { Totp.code(enrollment.secret, Totp.step() + it) == c } }
            val wrong = assertFailsWith<AuthApiException> { call { confirmTotp(it, wrongCode) } }
            assertEquals(AuthErrorCode.INVALID_SECOND_FACTOR, wrong.code)
            // steps -1, 0 and +1 in this order: each code works once
            val recoveryCodes = call { confirmTotp(it, Totp.code(enrollment.secret, Totp.step() - 1)) }
            totpOn = true
            assertEquals(10, recoveryCodes.size)
            val again = assertFailsWith<AuthApiException> { call { confirmTotp(it, "123456") } }
            assertEquals(409, again.status, "confirm without a pending enrolment")
            assertTrue(call { account(it) }.totpEnabled)

            // login now asks for the second factor
            val challenge = assertIs<LoginOutcome.SecondFactorRequired>(repository.login(backend, username, currentPassword))
            assertTrue(SecondFactorMethod.Totp in challenge.methods)
            assertIs<LoginOutcome.SignedIn>(
                repository.submitTotpCode(backend, challenge.challenge, Totp.code(enrollment.secret, Totp.step()).chunked(3).joinToString(" "))
            )
            val second = assertIs<LoginOutcome.SecondFactorRequired>(repository.login(backend, username, currentPassword))
            assertIs<LoginOutcome.SignedIn>(repository.submitRecoveryCode(backend, second.challenge, recoveryCodes.first()))
            assertEquals(9, call { account(it) }.recoveryCodesLeft)

            // step-up with password and code
            call { reauth(it, currentPassword, Totp.code(enrollment.secret, Totp.step() + 1)) }
            assertEquals(10, call { regenerateRecoveryCodes(it) }.size)

            if (authenticator != null) {
                // add a passkey like the account screen: begin, Credential Manager, finish
                val begin = call { beginAddPasskey(it) }
                val created = call { finishAddPasskey(it, begin.ceremonyId, authenticator.create(begin.publicKey), name = null) }
                val listed = call { passkeys(it) }.single { it.id == created.id }
                assertTrue(listed.backedUp)
                assertEquals("Bitwarden", listed.provider)

                // discoverable passkey login, without username or second factor
                val ceremony = repository.beginPasskeyLogin(backend)
                assertIs<LoginOutcome.SignedIn>(repository.finishPasskeyLogin(backend, ceremony.ceremonyId, authenticator.get(ceremony.publicKey)))

                // step-up with the passkey
                val reauth = call { beginReauthPasskey(it) }
                call { finishReauthPasskey(it, reauth.ceremonyId, authenticator.get(reauth.publicKey)) }
                call { renamePasskey(it, created.id, "Live test key") }
                call { deletePasskey(it, created.id) }
            }

            call { disableTotp(it) }
            totpOn = false
        } finally {
            // set the account back, as far as the session allows
            runCatching {
                if (totpOn) call { disableTotp(it) }
                call { passkeys(it) }.forEach { p -> call { deletePasskey(it, p.id) } }
                if (currentPassword != password) call { changePassword(it, currentPassword, password) }
                call { updateAccount(it, AccountUpdate(displayName = original.displayName)) }
            }
        }
        assertNotNull(store.sessions[1])
        repository.signOut(backend)
    }

    companion object {
        /** de.markusressel.mkdocseditor.debug signed with .ci/debug.keystore */
        const val CI_DEBUG_ORIGIN = "android:apk-key-hash:dKdF-sJKv73wpzCDGHH-pt4kbGvS4_hiSSQnH8TTcEg"
    }
}
