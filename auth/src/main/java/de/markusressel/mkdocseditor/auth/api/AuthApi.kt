package de.markusressel.mkdocseditor.auth.api

import java.time.Instant

/**
 * The account endpoints of one backend, see docs/auth.md of MkDocsEditor-Backend.
 *
 * Every call that creates a session asks for a bearer session. Authenticated calls take the
 * session token. All calls throw [AuthApiException] for error answers and
 * [java.io.IOException] for network errors.
 */
interface AuthApi {

    // public

    suspend fun status(): AuthStatus

    suspend fun login(username: String, password: String, deviceName: String): LoginResponse

    /**
     * Second step of a password login: a TOTP [code] or a [recoveryCode]
     */
    suspend fun secondFactor(
        challenge: String,
        code: String? = null,
        recoveryCode: String? = null,
        deviceName: String,
    ): LoginResponse

    /**
     * @param challenge the login challenge when the passkey is the second factor, null for a
     * discoverable passkey login without username
     */
    suspend fun beginPasskeyLogin(challenge: String? = null): PasskeyCeremony

    suspend fun finishPasskeyLogin(ceremonyId: String, credentialJson: String, deviceName: String): LoginResponse

    suspend fun inspectLink(linkToken: String): LinkInfo

    /**
     * Redeems a setup or invite link (with [username]) or a reset link (without)
     */
    suspend fun redeemLink(
        linkToken: String,
        username: String?,
        displayName: String?,
        password: String,
        deviceName: String,
    ): LoginResponse

    suspend fun beginLinkPasskey(linkToken: String, username: String?, displayName: String?): PasskeyCeremony

    suspend fun finishLinkPasskey(
        ceremonyId: String,
        credentialJson: String,
        passkeyName: String?,
        deviceName: String,
    ): LoginResponse

    // authenticated

    suspend fun whoAmI(token: String): WhoAmI

    suspend fun logout(token: String)

    /**
     * Step-up with the password and/or a TOTP code
     *
     * @return until when the session counts as recently authenticated
     */
    suspend fun reauth(token: String, password: String?, code: String?): Instant?

    suspend fun beginReauthPasskey(token: String): PasskeyCeremony

    suspend fun finishReauthPasskey(token: String, ceremonyId: String, credentialJson: String): Instant?

    // account

    suspend fun account(token: String): AccountInfo

    suspend fun updateAccount(token: String, update: AccountUpdate): AccountInfo

    suspend fun changePassword(token: String, currentPassword: String?, newPassword: String)

    suspend fun beginTotp(token: String): TotpEnrollment

    /**
     * @return the recovery codes, shown once
     */
    suspend fun confirmTotp(token: String, code: String): List<String>

    suspend fun disableTotp(token: String)

    suspend fun regenerateRecoveryCodes(token: String): List<String>

    suspend fun passkeys(token: String): List<PasskeyInfo>

    suspend fun beginAddPasskey(token: String): PasskeyCeremony

    suspend fun finishAddPasskey(token: String, ceremonyId: String, credentialJson: String, name: String?): PasskeyInfo

    suspend fun renamePasskey(token: String, id: String, name: String): PasskeyInfo

    suspend fun deletePasskey(token: String, id: String)

    suspend fun sessions(token: String): List<SessionInfo>

    suspend fun revokeSession(token: String, id: String)

    /**
     * @return how many sessions were revoked
     */
    suspend fun revokeOtherSessions(token: String): Int
}

/**
 * Creates the [AuthApi] of a backend
 */
fun interface AuthApiFactory {
    /**
     * @param baseUrl the backend URL, like `https://backend.wiki.example.com/`
     */
    fun create(baseUrl: String): AuthApi
}
