package de.markusressel.mkdocseditor.auth.token

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Minimal string key/value storage, so the token store can be tested without Android
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class SharedPreferencesKeyValueStore(
    private val preferences: SharedPreferences,
) : KeyValueStore {
    override fun get(key: String): String? = preferences.getString(key, null)

    override fun put(key: String, value: String) = preferences.edit { putString(key, value) }

    override fun remove(key: String) = preferences.edit { remove(key) }
}
