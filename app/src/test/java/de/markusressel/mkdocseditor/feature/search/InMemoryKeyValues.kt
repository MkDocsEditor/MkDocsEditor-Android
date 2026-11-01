package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.auth.token.KeyValueStore

internal class InMemoryKeyValues : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
