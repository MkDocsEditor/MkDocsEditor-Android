package de.markusressel.mkdocseditor.auth.api

import com.squareup.moshi.JsonClass
import java.time.Instant

/*
 * Request and response bodies of the account endpoints, see docs/auth.md of MkDocsEditor-Backend.
 */

@JsonClass(generateAdapter = true)
data class AuthStatus(
    val setupRequired: Boolean = false,
    val authDisabled: Boolean = false,
    val passkeys: PasskeySupport = PasskeySupport(),
    val passwordPolicy: PasswordPolicy = PasswordPolicy(),
    val legacyBasicAuth: Boolean = false,
)

/**
 * GET /server-info/: public facts about an instance for onboarding screens
 */
@JsonClass(generateAdapter = true)
data class ServerInfo(
    val name: String? = null,
    val version: String? = null,
    val protocolVersion: Int? = null,
    val backendUrl: String? = null,
    val wikiUrl: String? = null,
    val webClientUrl: String? = null,
    val setupRequired: Boolean = false,
    val authDisabled: Boolean = false,
    val passkeys: PasskeySupport = PasskeySupport(),
    val loginMethods: List<String> = emptyList(),
    val require2FA: Boolean = false,
    /** optional API features of this server, see [ServerFeature] */
    val features: List<String> = emptyList(),
)

/**
 * POST /account/device-links/: a single-use token that connects another device
 */
@JsonClass(generateAdapter = true)
data class DeviceLinkCreated(
    val token: String,
    val expiresAt: Instant? = null,
    val connectUrl: String? = null,
) {
    override fun toString() = "DeviceLinkCreated(expiresAt=$expiresAt)"
}

@JsonClass(generateAdapter = true)
data class PasskeySupport(
    val enabled: Boolean = false,
    val rpId: String? = null,
)

@JsonClass(generateAdapter = true)
data class PasswordPolicy(
    val minLength: Int = 10,
    val maxLength: Int = 256,
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val color: String? = null,
    /** version of the profile picture, null without one, see [avatarPath] */
    val avatar: String? = null,
    val secondFactorSetupRequired: Boolean = false,
    /** number of registered passkeys, sent by newer backends */
    val passkeyCount: Int? = null,
)

@JsonClass(generateAdapter = true)
data class SessionInfo(
    val id: String,
    val type: String? = null,
    val deviceName: String? = null,
    val userAgent: String? = null,
    val ip: String? = null,
    val createdAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val expiresAt: Instant? = null,
    val idleExpiresAt: Instant? = null,
    val current: Boolean = false,
)

/**
 * Answer of every endpoint that may create a session
 */
@JsonClass(generateAdapter = true)
data class LoginResponse(
    val status: String,
    val user: UserInfo? = null,
    val session: SessionInfo? = null,
    val token: String? = null,
    val challenge: String? = null,
    val methods: List<String> = emptyList(),
    val expiresAt: Instant? = null,
)

/**
 * Begin of a WebAuthn ceremony.
 *
 * @param publicKey the options JSON, passed as is to Credential Manager
 */
data class PasskeyCeremony(
    val ceremonyId: String,
    val publicKey: String,
)

@JsonClass(generateAdapter = true)
data class LinkInfo(
    val kind: String,
    val role: String? = null,
    val note: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val resetSecondFactors: Boolean = false,
    val expiresAt: Instant? = null,
)

@JsonClass(generateAdapter = true)
data class AccountInfo(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val color: String? = null,
    /** version of the profile picture, null without one, see [avatarPath] */
    val avatar: String? = null,
    val email: String? = null,
    val createdAt: Instant? = null,
    val hasPassword: Boolean = false,
    val totpEnabled: Boolean = false,
    val recoveryCodesLeft: Int = 0,
    val passkeyCount: Int = 0,
    val secondFactorSetupRequired: Boolean = false,
    val require2FA: Boolean = false,
    val reauthenticatedUntil: Instant? = null,
)

@JsonClass(generateAdapter = true)
data class AccountUpdate(
    val displayName: String? = null,
    val color: String? = null,
    val email: String? = null,
    val username: String? = null,
)

@JsonClass(generateAdapter = true)
data class TotpEnrollment(
    val secret: String,
    val otpauthUri: String,
    val expiresAt: Instant? = null,
)

@JsonClass(generateAdapter = true)
data class PasskeyInfo(
    val id: String,
    val name: String,
    val createdAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val backedUp: Boolean = false,
    val transports: List<String> = emptyList(),
    /** the passkey provider from the AAGUID, like "Bitwarden", when the backend knows it */
    val provider: String? = null,
)

@JsonClass(generateAdapter = true)
data class WhoAmI(
    val id: String? = null,
    val username: String,
    val displayName: String,
    val role: String,
    val color: String? = null,
    val authMethod: String? = null,
    val secondFactorSetupRequired: Boolean = false,
    val sessionId: String? = null,
)

/** roles of an account, see docs/auth.md of MkDocsEditor-Backend */
object AccountRole {
    const val ADMIN = "admin"
    const val EDITOR = "editor"
    const val READER = "reader"
}

/**
 * Stable error codes of the backend, see docs/auth.md section 10
 */
object ServerFeature {
    const val ACCESS_REQUESTS = "access-requests"
    const val AVATARS = "avatars"
}

/** POST /auth/access-requests/. The token is only shown once. */
@JsonClass(generateAdapter = true)
data class AccessRequestCreated(
    val requestId: String,
    val token: String,
    val status: String,
    val expiresAt: Instant? = null,
) {
    override fun toString() = "AccessRequestCreated(requestId=$requestId, status=$status, expiresAt=$expiresAt, token=***)"
}

object AccessRequestState {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
    const val EXPIRED = "expired"
}

/** POST /auth/access-requests/status/ */
@JsonClass(generateAdapter = true)
data class AccessRequestStatus(
    val status: String,
    val inviteToken: String? = null,
) {
    override fun toString() = "AccessRequestStatus(status=$status, inviteToken=***)"
}

/** An entry of GET /admin/access-requests/. Name and message are only claimed by the one who asks. */
@JsonClass(generateAdapter = true)
data class AccessRequestInfo(
    val id: String,
    val name: String,
    val message: String? = null,
    val status: String,
    val createdAt: Instant? = null,
    val expiresAt: Instant? = null,
    val documentId: String? = null,
    val documentPath: String? = null,
    val invitedByAccountId: String? = null,
    val invitedByName: String? = null,
)

object AuthErrorCode {
    const val INVALID_ACCESS_REQUEST = "invalid-access-request"
    const val ACCESS_REQUESTS_DISABLED = "access-requests-disabled"
    const val TOO_MANY_ACCESS_REQUESTS = "too-many-access-requests"
    const val BAD_REQUEST = "bad-request"
    const val INVALID_USERNAME = "invalid-username"
    const val WEAK_PASSWORD = "weak-password"
    const val INVALID_LINK = "invalid-link"
    const val PASSKEY_REGISTRATION_FAILED = "passkey-registration-failed"
    const val UNAUTHENTICATED = "unauthenticated"
    const val INVALID_CREDENTIALS = "invalid-credentials"
    const val INVALID_SECOND_FACTOR = "invalid-second-factor"
    const val CHALLENGE_EXPIRED = "challenge-expired"
    const val PASSKEY_VERIFICATION_FAILED = "passkey-verification-failed"
    const val FORBIDDEN = "forbidden"
    const val ACCOUNT_DISABLED = "account-disabled"
    const val REAUTH_REQUIRED = "reauth-required"
    const val SECOND_FACTOR_SETUP_REQUIRED = "second-factor-setup-required"
    const val PASSKEYS_DISABLED = "passkeys-disabled"
    const val USERNAME_TAKEN = "username-taken"
    const val TOTP_ALREADY_ENABLED = "totp-already-enabled"
    const val TOTP_NOT_ENABLED = "totp-not-enabled"
    const val LAST_LOGIN_METHOD = "last-login-method"
    const val SECOND_FACTOR_REQUIRED = "second-factor-required"
    const val RATE_LIMITED = "rate-limited"
    const val CONFLICT = "conflict"
}

/**
 * Where a profile picture is, relative to the backend URL. The path changes with every new picture,
 * so it can be cached for good.
 *
 * @param accountId the stable id of the account
 * @param avatar the version of its picture, as the account or the presence sends it
 */
fun avatarPath(accountId: String, avatar: String): String =
    "avatars/${java.net.URLEncoder.encode(accountId, Charsets.UTF_8)}/${java.net.URLEncoder.encode(avatar, Charsets.UTF_8)}/"
