package de.markusressel.mkdocseditor.auth.token

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccessRequestStoreTest {

    private val values = mutableMapOf<String, String>()
    private val storage = object : KeyValueStore {
        override fun get(key: String) = values[key]
        override fun put(key: String, value: String) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }
    private val cipher = object : TokenCipher {
        override fun encrypt(plain: ByteArray) = plain.reversedArray()
        override fun decrypt(data: ByteArray) = data.reversedArray()
    }
    private val request = PendingAccessRequest("https://b/", "mda_secret", "https://c/join/1", "Wiki")

    @Test
    fun `reads back what it stored`() {
        val store = AccessRequestStore(storage, cipher)
        store.put(request)
        assertEquals(request, store.get())
        store.clear()
        assertNull(store.get())
    }

    @Test
    fun `an unreadable request is dropped`() {
        values["request"] = "not base64 !"
        assertNull(AccessRequestStore(storage, cipher).get())
        assertTrue(values.isEmpty())
    }

    @Test
    fun `the token stays out of logs`() {
        assertFalse("mda_secret" in request.toString())
    }
}
