package de.markusressel.mkdocseditor.auth.token

import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EncryptedTokenStoreTest {

    private class MapKeyValueStore : KeyValueStore {
        val map = mutableMapOf<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) {
            map[key] = value
        }

        override fun remove(key: String) {
            map.remove(key)
        }
    }

    private fun newKey(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val session = AuthSession(
        token = "secret-token-value",
        expiresAt = Instant.ofEpochSecond(1_900_000_000),
        username = "alice",
    )

    @Test
    fun `stores and reads a session per backend`() {
        val key = newKey()
        val store = EncryptedTokenStore(MapKeyValueStore(), AesGcmTokenCipher { key })

        store.put(1, session)
        store.put(2, session.copy(username = "bob", expiresAt = null))

        assertEquals(session, store.get(1))
        assertEquals("bob", store.get(2)?.username)
        assertNull(store.get(2)?.expiresAt)
        assertNull(store.get(3))
    }

    @Test
    fun `does not store the token in plain text`() {
        val storage = MapKeyValueStore()
        val key = newKey()
        EncryptedTokenStore(storage, AesGcmTokenCipher { key }).put(1, session)

        val raw = storage.map.values.single()
        assertFalse(raw.contains(session.token))
        assertFalse(raw.contains(session.username))
    }

    @Test
    fun `drops a session that cannot be decrypted`() {
        val storage = MapKeyValueStore()
        val key = newKey()
        EncryptedTokenStore(storage, AesGcmTokenCipher { key }).put(1, session)

        // a different key, like after restoring a backup on a new device
        val otherKey = newKey()
        val store = EncryptedTokenStore(storage, AesGcmTokenCipher { otherKey })

        assertNull(store.get(1))
        assertTrue(storage.map.isEmpty())
    }

    @Test
    fun `remove deletes the session`() {
        val key = newKey()
        val store = EncryptedTokenStore(MapKeyValueStore(), AesGcmTokenCipher { key })
        store.put(1, session)
        store.remove(1)
        assertNull(store.get(1))
    }

    @Test
    fun `cipher uses a fresh IV per message`() {
        val key = newKey()
        val cipher = AesGcmTokenCipher { key }
        val a = cipher.encrypt("same".encodeToByteArray())
        val b = cipher.encrypt("same".encodeToByteArray())
        assertFalse(a.contentEquals(b))
        assertEquals("same", cipher.decrypt(a).decodeToString())
    }

    @Test
    fun `session expiry respects the leeway`() {
        val now = Instant.ofEpochSecond(1000)
        assertFalse(session.copy(expiresAt = null).isExpired(now))
        assertFalse(session.copy(expiresAt = now.plusSeconds(60)).isExpired(now))
        assertTrue(session.copy(expiresAt = now.plusSeconds(10)).isExpired(now))
        assertTrue(session.copy(expiresAt = now.minusSeconds(1)).isExpired(now))
    }
}
