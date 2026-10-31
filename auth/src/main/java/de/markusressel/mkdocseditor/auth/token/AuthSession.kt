package de.markusressel.mkdocseditor.auth.token

import java.time.Instant

/**
 * A signed-in session on one backend.
 *
 * @param token the bearer token sent in the Authorization header
 * @param expiresAt when the server stops accepting [token], or null if the server did not say
 * @param username the user this session belongs to
 */
data class AuthSession(
    val token: String,
    val expiresAt: Instant?,
    val username: String,
) {
    /**
     * @return true if the token has expired at [now] (with [leewaySeconds] of tolerance for clock skew)
     */
    fun isExpired(now: Instant, leewaySeconds: Long = 30): Boolean {
        val expiry = expiresAt ?: return false
        return !now.isBefore(expiry.minusSeconds(leewaySeconds))
    }
}
