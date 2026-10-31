package de.markusressel.mkdocseditor.auth

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.markusressel.mkdocseditor.auth.api.OkHttpAuthApi
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.domain.BackendRef
import de.markusressel.mkdocseditor.auth.domain.LoginOutcome
import de.markusressel.mkdocseditor.auth.link.AuthLinkParser
import de.markusressel.mkdocseditor.auth.link.LinkKind
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assume.assumeTrue
import org.junit.Before
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Invite links against a running backend: an admin creates invites, and new accounts are
 * created from them with a password and with a passkey, like the app's link screen. Skipped
 * unless these Gradle properties are set:
 *
 * - `-Pmkdocs.backend.url=http://127.0.0.1:7413`
 * - `-Pmkdocs.backend.admin=admin:password` (an admin without TOTP)
 * - optional `-Pmkdocs.backend.passkeyOrigin=...`, see [LiveAccountTest]
 *
 * The backend needs `server.auth.clientUrl`, so invites come with a URL. Each run leaves two
 * new editor accounts (`live-…`) behind, so use a test backend.
 */
class LiveLinkTest {

    private val baseUrl = System.getProperty("mkdocs.backend.url")?.trimEnd('/')?.plus("/")
    private val admin = System.getProperty("mkdocs.backend.admin")?.split(":", limit = 2)
    private val passkeyOrigin = System.getProperty("mkdocs.backend.passkeyOrigin") ?: LiveAccountTest.CI_DEBUG_ORIGIN

    private val http = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).build()
    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    @Before
    fun requireBackend() {
        assumeTrue("set -Pmkdocs.backend.url and -Pmkdocs.backend.admin", baseUrl != null && admin?.size == 2)
    }

    /** POST /admin/invites/, which the app links out to the web client for */
    private fun createInvite(token: String): String {
        val request = Request.Builder()
            .url("${baseUrl}admin/invites/")
            .header("Authorization", "Bearer $token")
            .post("""{"role":"editor","expiresInHours":1,"note":"LiveLinkTest"}""".toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "create invite: HTTP ${response.code}" }
            val body = requireNotNull(mapAdapter.fromJson(response.body.string()))
            return requireNotNull(body["url"] as? String) { "no invite url, is server.auth.clientUrl set?" }
        }
    }

    @Test
    fun `invites create accounts with a password and with a passkey`(): Unit = runBlocking {
        val (adminName, adminPassword) = admin!!
        val backend = BackendRef(1, baseUrl!!)
        val adminRepository = AuthRepository(InMemoryTokenStore(), { OkHttpAuthApi(it, http) }, deviceName = "LiveLinkTest admin")
        assertIs<LoginOutcome.SignedIn>(adminRepository.login(backend, adminName, adminPassword))
        val adminToken = assertNotNull(adminRepository.token(1))
        val suffix = System.currentTimeMillis().toString(36)

        // with a password, from the invite URL as the app receives it
        val link = assertNotNull(AuthLinkParser.parse(createInvite(adminToken)))
        assertEquals(LinkKind.Invite, link.kind)
        val store = InMemoryTokenStore()
        val newUser = AuthRepository(store, { OkHttpAuthApi(it, http) }, deviceName = "LiveLinkTest")
        assertEquals("editor", newUser.inspectLink(backend, link).role)
        val signedIn = assertIs<LoginOutcome.SignedIn>(
            newUser.redeemLinkWithPassword(backend, link, "live-pw-$suffix", "Live Password", "a long live passphrase")
        )
        assertEquals("live-pw-$suffix", signedIn.user?.username)
        assertEquals("live-pw-$suffix", newUser.authenticated(backend) { account(it) }.username)

        // with a passkey only
        val rpId = newUser.status(backend).passkeys.rpId ?: return@runBlocking
        val authenticator = SoftwareAuthenticator(rpId, passkeyOrigin)
        val passkeyLink = assertNotNull(AuthLinkParser.parse(createInvite(adminToken)))
        val passkeyUser = AuthRepository(InMemoryTokenStore(), { OkHttpAuthApi(it, http) }, deviceName = "LiveLinkTest passkey")
        val ceremony = passkeyUser.beginLinkPasskey(backend, passkeyLink, "live-pk-$suffix", null)
        assertIs<LoginOutcome.SignedIn>(
            passkeyUser.finishLinkPasskey(backend, ceremony.ceremonyId, authenticator.create(ceremony.publicKey), passkeyName = null)
        )
        val account = passkeyUser.authenticated(backend) { account(it) }
        assertEquals(false, account.hasPassword)
        assertEquals(1, account.passkeyCount)

        // and signs in again with it
        val login = passkeyUser.beginPasskeyLogin(backend)
        assertIs<LoginOutcome.SignedIn>(passkeyUser.finishPasskeyLogin(backend, login.ceremonyId, authenticator.get(login.publicKey)))
    }
}
