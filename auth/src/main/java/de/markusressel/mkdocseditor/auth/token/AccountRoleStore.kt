package de.markusressel.mkdocseditor.auth.token

import java.security.MessageDigest

/**
 * Remembers the role of the account signed in to each backend, so screens know it at once and offline. A role
 * belongs to one account, so it is only returned for the account it was stored for.
 */
class AccountRoleStore(
    private val storage: KeyValueStore,
) {
    @Synchronized
    fun get(backendId: Long, username: String): String? {
        val stored = storage.get(key(backendId)) ?: return null
        if (stored.substringBefore(SEPARATOR) != hashOf(username)) return null
        return stored.substringAfter(SEPARATOR, "").ifEmpty { null }
    }

    @Synchronized
    fun put(backendId: Long, username: String, role: String) = storage.put(key(backendId), hashOf(username) + SEPARATOR + role)

    @Synchronized
    fun remove(backendId: Long) = storage.remove(key(backendId))

    private fun key(backendId: Long) = "role_$backendId"

    /** the username stays out of the file */
    private fun hashOf(username: String): String =
        MessageDigest.getInstance("SHA-256").digest(username.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    private companion object {
        const val SEPARATOR = ':'
    }
}
