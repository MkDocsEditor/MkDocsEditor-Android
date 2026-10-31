package de.markusressel.mkdocseditor.auth

import de.markusressel.mkdocseditor.auth.api.AccountInfo
import de.markusressel.mkdocseditor.auth.api.AccountUpdate
import de.markusressel.mkdocseditor.auth.api.AuthApi
import de.markusressel.mkdocseditor.auth.api.AuthStatus
import de.markusressel.mkdocseditor.auth.api.LinkInfo
import de.markusressel.mkdocseditor.auth.api.LoginResponse
import de.markusressel.mkdocseditor.auth.api.PasskeyCeremony
import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import de.markusressel.mkdocseditor.auth.api.SessionInfo
import de.markusressel.mkdocseditor.auth.api.DeviceLinkCreated
import de.markusressel.mkdocseditor.auth.api.TotpEnrollment
import de.markusressel.mkdocseditor.auth.api.WhoAmI
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.auth.token.TokenStore
import kotlinx.coroutines.flow.MutableSharedFlow
import java.time.Instant

/**
 * [AuthApi] for tests: every call is recorded in [calls] and answered by the matching handler
 */
open class FakeAuthApi : AuthApi {

    val calls = mutableListOf<String>()

    var onLogin: (String, String) -> LoginResponse = { _, _ -> error("unexpected login") }
    var onSecondFactor: (String, String?, String?) -> LoginResponse = { _, _, _ -> error("unexpected second factor") }
    var onFinishPasskeyLogin: (String, String) -> LoginResponse = { _, _ -> error("unexpected passkey login") }
    var onRedeem: (String, String?, String) -> LoginResponse = { _, _, _ -> error("unexpected redeem") }
    var onAccount: (String) -> AccountInfo = { error("unexpected account") }
    var onLogout: (String) -> Unit = {}
    var onWhoAmI: (String) -> WhoAmI = { error("unexpected whoami") }
    var onReauth: (String?, String?) -> Instant? = { _, _ -> Instant.EPOCH }
    var onChangePassword: (String?, String) -> Unit = { _, _ -> }
    var onBeginTotp: () -> TotpEnrollment = { TotpEnrollment("SECRET", "otpauth://totp/x?secret=SECRET") }
    var onConfirmTotp: (String) -> List<String> = { List(10) { "code-$it" } }
    var onSessions: () -> List<SessionInfo> = { emptyList() }
    var onPasskeys: () -> List<PasskeyInfo> = { emptyList() }
    var onDeletePasskey: (String) -> Unit = {}
    var onRevokeSession: (String) -> Unit = {}
    var status = AuthStatus()
    var onServerInfo: () -> ServerInfo = { ServerInfo(name = "Wiki", loginMethods = listOf("password", "device-link")) }
    var onRedeemDeviceLink: (String, String?) -> LoginResponse = { _, _ -> error("unexpected device link") }
    var linkInfo = LinkInfo(kind = "invite", role = "editor")
    var ceremony = PasskeyCeremony("cer", """{"challenge":"abc"}""")
    var onBeginAddPasskey: () -> PasskeyCeremony = { ceremony }
    var onFinishAddPasskey: (String?) -> PasskeyInfo = { name -> PasskeyInfo("new", name ?: "Passkey") }

    override suspend fun status(): AuthStatus = status.also { calls += "status" }

    override suspend fun serverInfo(): ServerInfo = onServerInfo().also { calls += "serverInfo" }

    override suspend fun redeemDeviceLink(token: String, username: String?, deviceName: String): LoginResponse {
        calls += "redeemDeviceLink:$username:$deviceName"
        return onRedeemDeviceLink(token, username)
    }

    override suspend fun createDeviceLink(token: String, deviceName: String?) =
        DeviceLinkCreated("mdd_" + "a".repeat(43)).also { calls += "createDeviceLink" }

    override suspend fun login(username: String, password: String, deviceName: String): LoginResponse {
        calls += "login:$username:$deviceName"
        return onLogin(username, password)
    }

    override suspend fun secondFactor(challenge: String, code: String?, recoveryCode: String?, deviceName: String): LoginResponse {
        calls += "secondFactor:$challenge"
        return onSecondFactor(challenge, code, recoveryCode)
    }

    override suspend fun beginPasskeyLogin(challenge: String?): PasskeyCeremony = ceremony.also { calls += "beginPasskeyLogin:$challenge" }

    override suspend fun finishPasskeyLogin(ceremonyId: String, credentialJson: String, deviceName: String): LoginResponse {
        calls += "finishPasskeyLogin:$ceremonyId"
        return onFinishPasskeyLogin(ceremonyId, credentialJson)
    }

    override suspend fun inspectLink(linkToken: String): LinkInfo = linkInfo.also { calls += "inspect" }

    override suspend fun redeemLink(
        linkToken: String,
        username: String?,
        displayName: String?,
        password: String,
        deviceName: String
    ): LoginResponse {
        calls += "redeem:$username:$displayName"
        return onRedeem(linkToken, username, password)
    }

    override suspend fun beginLinkPasskey(linkToken: String, username: String?, displayName: String?) =
        ceremony.also { calls += "beginLinkPasskey:$username" }

    override suspend fun finishLinkPasskey(
        ceremonyId: String,
        credentialJson: String,
        passkeyName: String?,
        deviceName: String
    ): LoginResponse {
        calls += "finishLinkPasskey:$ceremonyId"
        return onRedeem(ceremonyId, null, credentialJson)
    }

    override suspend fun whoAmI(token: String) = onWhoAmI(token)
    override suspend fun logout(token: String) {
        calls += "logout:$token"
        onLogout(token)
    }

    override suspend fun reauth(token: String, password: String?, code: String?): Instant? {
        calls += "reauth:$password:$code"
        return onReauth(password, code)
    }

    override suspend fun beginReauthPasskey(token: String) = ceremony.also { calls += "beginReauthPasskey" }
    override suspend fun finishReauthPasskey(token: String, ceremonyId: String, credentialJson: String): Instant? {
        calls += "finishReauthPasskey"
        return Instant.EPOCH
    }

    override suspend fun account(token: String): AccountInfo {
        calls += "account:$token"
        return onAccount(token)
    }

    override suspend fun updateAccount(token: String, update: AccountUpdate): AccountInfo {
        calls += "updateAccount:$update"
        return onAccount(token)
    }

    override suspend fun changePassword(token: String, currentPassword: String?, newPassword: String) {
        calls += "changePassword"
        onChangePassword(currentPassword, newPassword)
    }

    override suspend fun beginTotp(token: String) = onBeginTotp().also { calls += "beginTotp" }
    override suspend fun confirmTotp(token: String, code: String) = onConfirmTotp(code).also { calls += "confirmTotp:$code" }
    override suspend fun disableTotp(token: String) {
        calls += "disableTotp"
    }

    override suspend fun regenerateRecoveryCodes(token: String) = List(10) { "new-$it" }.also { calls += "regenerate" }
    override suspend fun passkeys(token: String) = onPasskeys().also { calls += "passkeys" }
    override suspend fun beginAddPasskey(token: String) = onBeginAddPasskey().also { calls += "beginAddPasskey" }
    override suspend fun finishAddPasskey(token: String, ceremonyId: String, credentialJson: String, name: String?) =
        onFinishAddPasskey(name).also { calls += "finishAddPasskey:$name" }

    override suspend fun renamePasskey(token: String, id: String, name: String) =
        PasskeyInfo(id, name).also { calls += "renamePasskey:$id:$name" }

    override suspend fun deletePasskey(token: String, id: String) {
        calls += "deletePasskey:$id"
        onDeletePasskey(id)
    }

    override suspend fun sessions(token: String) = onSessions().also { calls += "sessions" }
    override suspend fun revokeSession(token: String, id: String) {
        calls += "revokeSession:$id"
        onRevokeSession(id)
    }

    override suspend fun revokeOtherSessions(token: String) = 2.also { calls += "revokeOthers" }
}

class InMemoryTokenStore : TokenStore {
    val sessions = mutableMapOf<Long, AuthSession>()
    override val changes = MutableSharedFlow<Long>(extraBufferCapacity = 64)
    override fun get(backendId: Long) = sessions[backendId]
    override fun put(backendId: Long, session: AuthSession) {
        sessions[backendId] = session
        changes.tryEmit(backendId)
    }

    override fun remove(backendId: Long) {
        sessions.remove(backendId)
        changes.tryEmit(backendId)
    }
}

fun okResponse(token: String = "mds_token", username: String = "markus", secondFactorSetupRequired: Boolean = false) =
    LoginResponse(
        status = "ok",
        user = de.markusressel.mkdocseditor.auth.api.UserInfo(
            id = "id1",
            username = username,
            displayName = username.replaceFirstChar(Char::uppercase),
            role = "editor",
            secondFactorSetupRequired = secondFactorSetupRequired,
        ),
        session = SessionInfo(id = "s1", expiresAt = Instant.parse("2030-01-01T00:00:00Z")),
        token = token,
    )
