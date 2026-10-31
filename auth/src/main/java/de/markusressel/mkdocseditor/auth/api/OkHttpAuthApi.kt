package de.markusressel.mkdocseditor.auth.api

import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonReader
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okio.Buffer
import java.time.Instant

/**
 * [AuthApi] over OkHttp
 */
class OkHttpAuthApi(
    baseUrl: String,
    client: OkHttpClient,
) : AuthApi {

    private val http = AuthHttp(baseUrl, client, authMoshi())
    private val moshi = http.moshi

    private val statusAdapter = moshi.adapter(AuthStatus::class.java)
    private val loginAdapter = moshi.adapter(LoginResponse::class.java)
    private val linkAdapter = moshi.adapter(LinkInfo::class.java)
    private val whoAmIAdapter = moshi.adapter(WhoAmI::class.java)
    private val reauthAdapter = moshi.adapter(ReauthResponse::class.java)
    private val accountAdapter = moshi.adapter(AccountInfo::class.java)
    private val accountUpdateAdapter = moshi.adapter(AccountUpdate::class.java)
    private val totpAdapter = moshi.adapter(TotpEnrollment::class.java)
    private val recoveryCodesAdapter = moshi.adapter(RecoveryCodesResponse::class.java)
    private val passkeyAdapter = moshi.adapter(PasskeyInfo::class.java)
    private val passkeysAdapter = moshi.adapter<List<PasskeyInfo>>(
        Types.newParameterizedType(List::class.java, PasskeyInfo::class.java)
    )
    private val sessionsAdapter = moshi.adapter<List<SessionInfo>>(
        Types.newParameterizedType(List::class.java, SessionInfo::class.java)
    )
    private val revokedAdapter = moshi.adapter(RevokedResponse::class.java)
    private val serverInfoAdapter = moshi.adapter(ServerInfo::class.java)
    private val deviceLinkAdapter = moshi.adapter(DeviceLinkCreated::class.java)

    override suspend fun status(): AuthStatus =
        requireNotNull(http.call("GET", "auth/status/", response = statusAdapter))

    override suspend fun serverInfo(): ServerInfo =
        requireNotNull(http.call("GET", "server-info/", response = serverInfoAdapter))

    override suspend fun redeemDeviceLink(token: String, username: String?, deviceName: String) =
        loginCall("auth/device-link/redeem/") {
            put("token", token)
            put("username", username)
            put("deviceName", deviceName)
            put("sessionType", SESSION_TYPE)
        }

    override suspend fun createDeviceLink(token: String, deviceName: String?): DeviceLinkCreated = requireNotNull(
        http.call(
            "POST", "account/device-links/", token = token,
            body = jsonObject { put("deviceName", deviceName) },
            response = deviceLinkAdapter,
        )
    )

    override suspend fun login(username: String, password: String, deviceName: String) = loginCall(
        "auth/login/"
    ) {
        put("username", username)
        put("password", password)
        put("sessionType", SESSION_TYPE)
        put("deviceName", deviceName)
    }

    override suspend fun secondFactor(
        challenge: String,
        code: String?,
        recoveryCode: String?,
        deviceName: String
    ) = loginCall("auth/login/second-factor/") {
        put("challenge", challenge)
        put("code", code)
        put("recoveryCode", recoveryCode)
        put("sessionType", SESSION_TYPE)
        put("deviceName", deviceName)
    }

    override suspend fun beginPasskeyLogin(challenge: String?) = ceremonyCall("auth/passkey/login/begin/", null) {
        put("challenge", challenge)
    }

    override suspend fun finishPasskeyLogin(ceremonyId: String, credentialJson: String, deviceName: String) =
        loginCall("auth/passkey/login/finish/") {
            put("ceremonyId", ceremonyId)
            putRaw("credential", credentialJson)
            put("sessionType", SESSION_TYPE)
            put("deviceName", deviceName)
        }

    override suspend fun inspectLink(linkToken: String): LinkInfo = requireNotNull(
        http.call("POST", "auth/link/inspect/", body = jsonObject { put("token", linkToken) }, response = linkAdapter)
    )

    override suspend fun redeemLink(
        linkToken: String,
        username: String?,
        displayName: String?,
        password: String,
        deviceName: String
    ) = loginCall("auth/link/redeem/") {
        put("token", linkToken)
        put("username", username)
        put("displayName", displayName)
        put("password", password)
        put("sessionType", SESSION_TYPE)
        put("deviceName", deviceName)
    }

    override suspend fun beginLinkPasskey(linkToken: String, username: String?, displayName: String?) =
        ceremonyCall("auth/link/passkey/begin/", null) {
            put("token", linkToken)
            put("username", username)
            put("displayName", displayName)
        }

    override suspend fun finishLinkPasskey(
        ceremonyId: String,
        credentialJson: String,
        passkeyName: String?,
        deviceName: String
    ) = loginCall("auth/link/passkey/finish/") {
        put("ceremonyId", ceremonyId)
        putRaw("credential", credentialJson)
        put("passkeyName", passkeyName)
        put("sessionType", SESSION_TYPE)
        put("deviceName", deviceName)
    }

    override suspend fun whoAmI(token: String): WhoAmI =
        requireNotNull(http.call("GET", "auth/whoami/", token = token, response = whoAmIAdapter))

    override suspend fun logout(token: String) {
        http.call<Unit>("POST", "auth/logout/", token = token)
    }

    override suspend fun reauth(token: String, password: String?, code: String?): Instant? = http.call(
        "POST", "auth/reauth/", token = token,
        body = jsonObject {
            put("password", password)
            put("code", code)
        },
        response = reauthAdapter,
    )?.reauthenticatedUntil

    override suspend fun beginReauthPasskey(token: String) = ceremonyCall("auth/reauth/passkey/begin/", token) {}

    override suspend fun finishReauthPasskey(token: String, ceremonyId: String, credentialJson: String): Instant? =
        http.call(
            "POST", "auth/reauth/passkey/finish/", token = token,
            body = jsonObject {
                put("ceremonyId", ceremonyId)
                putRaw("credential", credentialJson)
            },
            response = reauthAdapter,
        )?.reauthenticatedUntil

    override suspend fun account(token: String): AccountInfo =
        requireNotNull(http.call("GET", "account/", token = token, response = accountAdapter))

    override suspend fun updateAccount(token: String, update: AccountUpdate): AccountInfo = requireNotNull(
        http.call(
            "PATCH", "account/", token = token,
            body = http.json(accountUpdateAdapter, update),
            response = accountAdapter,
        )
    )

    override suspend fun changePassword(token: String, currentPassword: String?, newPassword: String) {
        http.call<Unit>("PUT", "account/password/", token = token, body = jsonObject {
            put("currentPassword", currentPassword)
            put("newPassword", newPassword)
        })
    }

    override suspend fun beginTotp(token: String): TotpEnrollment =
        requireNotNull(http.call("POST", "account/totp/", token = token, response = totpAdapter))

    override suspend fun confirmTotp(token: String, code: String): List<String> = requireNotNull(
        http.call(
            "POST", "account/totp/confirm/", token = token,
            body = jsonObject { put("code", code) },
            response = recoveryCodesAdapter,
        )
    ).recoveryCodes

    override suspend fun disableTotp(token: String) {
        http.call<Unit>("DELETE", "account/totp/", token = token)
    }

    override suspend fun regenerateRecoveryCodes(token: String): List<String> = requireNotNull(
        http.call("POST", "account/recovery-codes/", token = token, response = recoveryCodesAdapter)
    ).recoveryCodes

    override suspend fun passkeys(token: String): List<PasskeyInfo> =
        http.call("GET", "account/passkeys/", token = token, response = passkeysAdapter).orEmpty()

    override suspend fun beginAddPasskey(token: String) = ceremonyCall("account/passkeys/begin/", token) {}

    override suspend fun finishAddPasskey(
        token: String,
        ceremonyId: String,
        credentialJson: String,
        name: String?
    ): PasskeyInfo = requireNotNull(
        http.call(
            "POST", "account/passkeys/finish/", token = token,
            body = jsonObject {
                put("ceremonyId", ceremonyId)
                putRaw("credential", credentialJson)
                put("name", name)
            },
            response = passkeyAdapter,
        )
    )

    override suspend fun renamePasskey(token: String, id: String, name: String): PasskeyInfo = requireNotNull(
        http.call(
            "PATCH", "account/passkeys/${segment(id)}/", token = token,
            body = jsonObject { put("name", name) },
            response = passkeyAdapter,
        )
    )

    override suspend fun deletePasskey(token: String, id: String) {
        http.call<Unit>("DELETE", "account/passkeys/${segment(id)}/", token = token)
    }

    override suspend fun sessions(token: String): List<SessionInfo> =
        http.call("GET", "account/sessions/", token = token, response = sessionsAdapter).orEmpty()

    override suspend fun revokeSession(token: String, id: String) {
        http.call<Unit>("DELETE", "account/sessions/${segment(id)}/", token = token)
    }

    override suspend fun revokeOtherSessions(token: String): Int =
        http.call("POST", "account/sessions/revoke-others/", token = token, response = revokedAdapter)?.revoked ?: 0

    private suspend fun loginCall(path: String, body: JsonObjectBuilder.() -> Unit): LoginResponse =
        requireNotNull(http.call("POST", path, body = jsonObject(body), response = loginAdapter))

    private suspend fun ceremonyCall(
        path: String,
        token: String?,
        body: JsonObjectBuilder.() -> Unit
    ): PasskeyCeremony {
        val raw = http.call("POST", path, token = token, body = jsonObject(body), response = RawObjectAdapter)
        return parseCeremony(requireNotNull(raw))
    }

    /**
     * ids are base32, but escape them anyway
     */
    private fun segment(id: String): String = java.net.URLEncoder.encode(id, Charsets.UTF_8).replace("+", "%20")

    @JsonClass(generateAdapter = true)
    internal data class ReauthResponse(val reauthenticatedUntil: Instant? = null)

    @JsonClass(generateAdapter = true)
    internal data class RecoveryCodesResponse(val recoveryCodes: List<String> = emptyList())

    @JsonClass(generateAdapter = true)
    internal data class RevokedResponse(val revoked: Int = 0)

    companion object {
        const val SESSION_TYPE = "bearer"

        /**
         * Parses `{"ceremonyId": "…", "publicKey": {…}}`, keeping `publicKey` as raw JSON
         */
        internal fun parseCeremony(json: String): PasskeyCeremony {
            var ceremonyId: String? = null
            var publicKey: String? = null
            JsonReader.of(Buffer().writeUtf8(json)).use { reader ->
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "ceremonyId" -> ceremonyId = reader.nextString()
                        "publicKey" -> publicKey = reader.readRawJson()
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
            }
            return PasskeyCeremony(
                ceremonyId = requireNotNull(ceremonyId) { "ceremonyId missing" },
                publicKey = requireNotNull(publicKey) { "publicKey missing" },
            )
        }
    }
}

/**
 * Passes the response body through as a string
 */
internal object RawObjectAdapter : com.squareup.moshi.JsonAdapter<String>() {
    override fun fromJson(reader: JsonReader): String = reader.readRawJson()
    override fun toJson(writer: com.squareup.moshi.JsonWriter, value: String?) {
        if (value == null) writer.nullValue() else writer.rawJsonValue(value)
    }
}
