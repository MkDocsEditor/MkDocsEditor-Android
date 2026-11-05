package de.markusressel.mkdocseditor.auth.token

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import timber.log.Timber
import java.util.Base64

/**
 * A request for access that waits for the admin, which can take days
 *
 * @param token proves that this device made the request, and gets the invite once it is approved
 * @param joinLink the link that started it, to come back to
 */
@JsonClass(generateAdapter = true)
data class PendingAccessRequest(
    val backendUrl: String,
    val token: String,
    val joinLink: String,
    val serverName: String,
    /** the user was told about the approval, which stays the answer until the account exists */
    val notified: Boolean = false,
) {
    override fun toString() = "PendingAccessRequest(backendUrl=$backendUrl, serverName=$serverName, token=***, joinLink=***)"
}

/**
 * Keeps the one request of this device encrypted, like [EncryptedTokenStore] does with sessions. A new request replaces it.
 */
class AccessRequestStore(
    private val storage: KeyValueStore,
    private val cipher: TokenCipher,
) {
    private val adapter = Moshi.Builder().build().adapter(PendingAccessRequest::class.java)

    @Synchronized
    fun get(): PendingAccessRequest? {
        val encoded = storage.get(KEY) ?: return null
        return try {
            adapter.fromJson(cipher.decrypt(Base64.getDecoder().decode(encoded)).decodeToString())
        } catch (e: Exception) {
            Timber.w(e, "Dropping an unreadable access request")
            storage.remove(KEY)
            null
        }
    }

    @Synchronized
    fun put(request: PendingAccessRequest) {
        val encrypted = cipher.encrypt(adapter.toJson(request).encodeToByteArray())
        storage.put(KEY, Base64.getEncoder().encodeToString(encrypted))
    }

    @Synchronized
    fun clear() = storage.remove(KEY)

    private companion object {
        const val KEY = "request"
    }
}
