package de.markusressel.mkdocseditor.auth.token

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.time.Instant
import java.util.Base64

/**
 * [TokenStore] that keeps every session encrypted with [cipher] in a [KeyValueStore].
 *
 * A session that cannot be decrypted anymore (for example after a restore from a backup,
 * where the Keystore key is missing) is dropped, so the user simply signs in again.
 */
class EncryptedTokenStore(
    private val storage: KeyValueStore,
    private val cipher: TokenCipher,
) : TokenStore {

    private val adapter = Moshi.Builder().build().adapter(StoredSession::class.java)

    private val _changes = MutableSharedFlow<Long>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val changes: Flow<Long> = _changes.asSharedFlow()

    @Synchronized
    override fun get(backendId: Long): AuthSession? {
        val encoded = storage.get(key(backendId)) ?: return null
        return try {
            val json = cipher.decrypt(Base64.getDecoder().decode(encoded)).decodeToString()
            requireNotNull(adapter.fromJson(json)).toAuthSession()
        } catch (e: Exception) {
            Timber.w(e, "Dropping unreadable session of backend $backendId")
            storage.remove(key(backendId))
            null
        }
    }

    @Synchronized
    override fun put(backendId: Long, session: AuthSession) {
        val json = adapter.toJson(session.toStoredSession())
        val encrypted = cipher.encrypt(json.encodeToByteArray())
        storage.put(key(backendId), Base64.getEncoder().encodeToString(encrypted))
        _changes.tryEmit(backendId)
    }

    @Synchronized
    override fun remove(backendId: Long) {
        storage.remove(key(backendId))
        _changes.tryEmit(backendId)
    }

    private fun key(backendId: Long) = "session_$backendId"

    @JsonClass(generateAdapter = true)
    internal data class StoredSession(
        val token: String,
        val expiresAtEpochSeconds: Long?,
        val username: String,
    )

    private fun AuthSession.toStoredSession() = StoredSession(
        token = token,
        expiresAtEpochSeconds = expiresAt?.epochSecond,
        username = username,
    )

    private fun StoredSession.toAuthSession() = AuthSession(
        token = token,
        expiresAt = expiresAtEpochSeconds?.let(Instant::ofEpochSecond),
        username = username,
    )
}
