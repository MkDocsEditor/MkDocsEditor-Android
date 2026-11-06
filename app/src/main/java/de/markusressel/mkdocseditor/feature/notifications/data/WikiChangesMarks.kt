package de.markusressel.mkdocseditor.feature.notifications.data

import de.markusressel.mkdocseditor.auth.token.KeyValueStore

/**
 * Remembers per server and account how far the feed was read, so a change never notifies twice, and which pages the
 * account edited, because the feed only shows the newest of those.
 *
 * The mark is a time and not an entry id: an editing session that goes on keeps its entry and moves it up the feed.
 */
internal class WikiChangesMarks(private val storage: KeyValueStore) {

    /** @return the time of the newest entry seen, in milliseconds, null before the first check */
    fun seenUntil(backendId: Long, accountId: String): Long? = storage.get(key(backendId, accountId))?.toLongOrNull()

    fun setSeenUntil(backendId: Long, accountId: String, time: Long) = storage.put(key(backendId, accountId), time.toString())

    fun editedPages(backendId: Long, accountId: String): Set<String> =
        storage.get(key(backendId, accountId) + EDITED)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()

    fun setEditedPages(backendId: Long, accountId: String, documentIds: Set<String>) =
        storage.put(key(backendId, accountId) + EDITED, documentIds.joinToString(","))

    private fun key(backendId: Long, accountId: String) = "$backendId/$accountId"

    private companion object {
        const val EDITED = "/edited"
    }
}
