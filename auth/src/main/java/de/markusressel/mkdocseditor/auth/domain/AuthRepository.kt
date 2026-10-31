package de.markusressel.mkdocseditor.auth.domain

import de.markusressel.mkdocseditor.auth.api.AuthApi
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthApiFactory
import de.markusressel.mkdocseditor.auth.api.AuthStatus
import de.markusressel.mkdocseditor.auth.api.LinkInfo
import de.markusressel.mkdocseditor.auth.api.LoginResponse
import de.markusressel.mkdocseditor.auth.api.PasskeyCeremony
import de.markusressel.mkdocseditor.auth.api.UserInfo
import de.markusressel.mkdocseditor.auth.link.AuthLink
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.auth.token.TokenStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.IOException
import java.time.Instant

/**
 * A backend as the auth layer sees it
 *
 * @param id the id of the backend config, the key of its session
 * @param baseUrl the URL of the backend API
 */
data class BackendRef(
    val id: Long,
    val baseUrl: String,
)

enum class SecondFactorMethod(val apiName: String) {
    Totp("totp"),
    RecoveryCode("recovery-code"),
    Passkey("passkey");

    companion object {
        fun fromApi(names: List<String>): Set<SecondFactorMethod> =
            names.mapNotNull { name -> entries.firstOrNull { it.apiName == name } }.toSet()
    }
}

sealed interface LoginOutcome {
    /**
     * The session is stored
     *
     * @param secondFactorSetupRequired the server requires a second factor before anything else works
     */
    data class SignedIn(
        val user: UserInfo?,
        val secondFactorSetupRequired: Boolean,
    ) : LoginOutcome

    /**
     * The password was right, a second factor must follow
     */
    data class SecondFactorRequired(
        val challenge: String,
        val methods: Set<SecondFactorMethod>,
        val expiresAt: Instant?,
    ) : LoginOutcome {
        override fun toString() = "SecondFactorRequired(methods=$methods, expiresAt=$expiresAt)"
    }
}

/**
 * There is no session for this backend, the user must sign in
 */
class NotSignedInException(val backendId: Long) : IOException("not signed in")

/**
 * Signs in to backends and keeps their sessions, see docs/auth.md of MkDocsEditor-Backend.
 *
 * Sessions are stored per backend config in the [TokenStore]. When the server says a
 * session is not valid anymore, it is dropped and [sessionEnded] emits the backend id,
 * so the UI can show the login. Local data (offline copies, unsynced edits) is never
 * touched here.
 *
 * @param deviceName sent at login, so the session list on the server shows this device
 */
class AuthRepository(
    private val tokenStore: TokenStore,
    private val apiFactory: AuthApiFactory,
    private val deviceName: String,
    private val clock: () -> Instant = Instant::now,
) {

    private val apis = mutableMapOf<String, AuthApi>()

    private val _sessionEnded = MutableSharedFlow<Long>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Emits the backend id whenever its session ended unexpectedly (expired, revoked)
     */
    val sessionEnded: Flow<Long> = _sessionEnded.asSharedFlow()

    /**
     * Emits the backend id whenever its session was stored or removed
     */
    val sessionChanges: Flow<Long> = tokenStore.changes

    @Synchronized
    fun api(backend: BackendRef): AuthApi = apis.getOrPut(backend.baseUrl) { apiFactory.create(backend.baseUrl) }

    /**
     * @return the stored, unexpired session of the backend
     */
    fun session(backendId: Long): AuthSession? {
        val session = tokenStore.get(backendId) ?: return null
        if (session.isExpired(clock())) {
            Timber.i("Session of backend $backendId expired")
            tokenStore.remove(backendId)
            return null
        }
        return session
    }

    /**
     * @return the bearer token of the backend, or null when signed out
     */
    fun token(backendId: Long): String? = session(backendId)?.token

    fun isSignedIn(backendId: Long): Boolean = session(backendId) != null

    /**
     * Called when the server rejected the session (HTTP 401 `unauthenticated`, websocket close 4001)
     */
    fun onUnauthenticated(backendId: Long) {
        if (tokenStore.get(backendId) != null) {
            Timber.i("Session of backend $backendId was rejected by the server")
            tokenStore.remove(backendId)
            _sessionEnded.tryEmit(backendId)
        }
    }

    suspend fun status(backend: BackendRef): AuthStatus = api(backend).status()

    suspend fun login(backend: BackendRef, username: String, password: String): LoginOutcome =
        handleLogin(backend, api(backend).login(username.trim(), password, deviceName))

    suspend fun submitTotpCode(backend: BackendRef, challenge: String, code: String): LoginOutcome =
        handleLogin(backend, api(backend).secondFactor(challenge, code = code.filter(Char::isDigit), deviceName = deviceName))

    suspend fun submitRecoveryCode(backend: BackendRef, challenge: String, recoveryCode: String): LoginOutcome =
        handleLogin(
            backend,
            api(backend).secondFactor(challenge, recoveryCode = recoveryCode.trim(), deviceName = deviceName)
        )

    /**
     * @param challenge the login challenge when the passkey is the second factor, null for a passkey login
     */
    suspend fun beginPasskeyLogin(backend: BackendRef, challenge: String? = null): PasskeyCeremony =
        api(backend).beginPasskeyLogin(challenge)

    suspend fun finishPasskeyLogin(backend: BackendRef, ceremonyId: String, credentialJson: String): LoginOutcome =
        handleLogin(backend, api(backend).finishPasskeyLogin(ceremonyId, credentialJson, deviceName))

    suspend fun inspectLink(backend: BackendRef, link: AuthLink): LinkInfo = api(backend).inspectLink(link.token)

    /**
     * Creates the account of a setup or invite link (with [username]), or sets the new password of a reset link
     */
    suspend fun redeemLinkWithPassword(
        backend: BackendRef,
        link: AuthLink,
        username: String?,
        displayName: String?,
        password: String,
    ): LoginOutcome = handleLogin(
        backend,
        api(backend).redeemLink(
            link.token,
            username = username?.trim(),
            displayName = displayName?.trim()?.ifBlank { null },
            password = password,
            deviceName = deviceName,
        )
    )

    suspend fun beginLinkPasskey(
        backend: BackendRef,
        link: AuthLink,
        username: String?,
        displayName: String?,
    ): PasskeyCeremony = api(backend).beginLinkPasskey(
        link.token,
        username = username?.trim(),
        displayName = displayName?.trim()?.ifBlank { null },
    )

    suspend fun finishLinkPasskey(
        backend: BackendRef,
        ceremonyId: String,
        credentialJson: String,
        passkeyName: String?,
    ): LoginOutcome = handleLogin(
        backend,
        api(backend).finishLinkPasskey(ceremonyId, credentialJson, passkeyName, deviceName)
    )

    /**
     * Ends the session on the server (best effort) and forgets it locally
     */
    suspend fun signOut(backend: BackendRef) {
        val token = tokenStore.get(backend.id)?.token
        tokenStore.remove(backend.id)
        if (token != null) {
            try {
                api(backend).logout(token)
            } catch (e: Exception) {
                // the local session is gone anyway, the server session expires on its own
                Timber.w(e, "Logout request failed")
            }
        }
    }

    /**
     * Forgets the local session without asking the server, for example when the backend config is deleted
     */
    fun forget(backendId: Long) {
        tokenStore.remove(backendId)
    }

    /**
     * Runs an authenticated call with the session of the backend. A rejected session is dropped.
     *
     * @throws NotSignedInException without session
     */
    suspend fun <T> authenticated(backend: BackendRef, block: suspend AuthApi.(token: String) -> T): T {
        val token = token(backend.id) ?: throw NotSignedInException(backend.id)
        try {
            return api(backend).block(token)
        } catch (e: AuthApiException) {
            if (e.isUnauthenticated) {
                onUnauthenticated(backend.id)
            }
            throw e
        }
    }

    private fun handleLogin(backend: BackendRef, response: LoginResponse): LoginOutcome {
        return when (response.status) {
            STATUS_SECOND_FACTOR_REQUIRED -> LoginOutcome.SecondFactorRequired(
                challenge = requireNotNull(response.challenge) { "challenge missing" },
                methods = SecondFactorMethod.fromApi(response.methods),
                expiresAt = response.expiresAt,
            )

            else -> {
                val token = response.token
                    ?: throw IOException("the server did not return a bearer token (status ${response.status})")
                tokenStore.put(
                    backend.id,
                    AuthSession(
                        token = token,
                        expiresAt = response.session?.expiresAt,
                        username = response.user?.username.orEmpty(),
                    )
                )
                LoginOutcome.SignedIn(
                    user = response.user,
                    secondFactorSetupRequired = response.user?.secondFactorSetupRequired == true,
                )
            }
        }
    }

    companion object {
        const val STATUS_SECOND_FACTOR_REQUIRED = "second-factor-required"
    }
}
