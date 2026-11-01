package de.markusressel.mkdocseditor.feature.search.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import javax.inject.Inject
import javax.inject.Singleton

private const val KEY = "terms"
private const val LIMIT = 10

/**
 * Remembers the terms of recent searches, the last used one first. Terms that only differ in
 * case count as the same term, because the search ignores case too.
 */
@Singleton
internal class RecentSearchStore(
    private val store: KeyValueStore,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesKeyValueStore(context.getSharedPreferences("recent_searches", Context.MODE_PRIVATE))
    )

    fun load(): List<String> = store.get(KEY)?.lines().orEmpty().filter { it.isNotBlank() }

    /**
     * @return the terms including the new one
     */
    fun remember(term: String): List<String> {
        val cleaned = term.trim().replace(Regex("\\s+"), " ")
        val terms = (listOf(cleaned) + load().filterNot { it.equals(cleaned, ignoreCase = true) }).take(LIMIT)
        store.put(KEY, terms.joinToString("\n"))
        return terms
    }
}
