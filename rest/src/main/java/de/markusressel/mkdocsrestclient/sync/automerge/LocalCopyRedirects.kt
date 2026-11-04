package de.markusressel.mkdocsrestclient.sync.automerge

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Follows moves of documents for the local copies. Document ids derive from paths, so a move
 * on the server changes the id. A copy stored under the old id, maybe with edits made offline,
 * is re-keyed to the new id before the document is opened again (docs/references.md section 7
 * of the backend). A connected session learns the new id from `document-renamed` instead.
 *
 * @param lookup asks the server about at most [MAX_IDS_PER_LOOKUP] ids at once, and returns the
 * new id of the documents that moved. An id without an answer did not move, or the document is
 * gone. It throws if there is no answer (offline, server error).
 */
class LocalCopyRedirects(
    private val store: LocalDocumentStore,
    private val lookup: suspend (oldIds: List<String>) -> Map<String, String>,
) {

    private val mutex = Mutex()

    /**
     * Asks the server about every stored copy and re-keys the moved ones. Copies whose
     * documents are gone stay where they are.
     *
     * @return old id to new id of the re-keyed copies
     */
    suspend fun followAll(): Map<String, String> = follow(extraId = null)

    /**
     * @param extraId an id without a stored copy to look up too
     * @return old id to new id of every moved id that was looked up
     */
    private suspend fun follow(extraId: String?): Map<String, String> = mutex.withLock {
        val ids = store.ids()
        val toLookUp = if (extraId != null && extraId !in ids) ids + extraId else ids
        val answers = toLookUp.chunked(MAX_IDS_PER_LOOKUP).flatMap { safeLookup(it).toList() }
        buildMap {
            answers.forEach { (oldId, newId) ->
                if (newId != oldId) {
                    if (oldId in ids) {
                        store.move(oldId, newId)
                        Timber.i("Local copy of $oldId follows the move to $newId")
                    }
                    put(oldId, newId)
                }
            }
        }
    }

    /**
     * Follows moves before [documentId] is opened: re-keys every moved copy, so a copy held
     * under an old id of this document is found under its id.
     *
     * @return the id to open: [documentId], or its new id if it moved
     */
    suspend fun beforeOpening(documentId: String): String = follow(extraId = documentId)[documentId] ?: documentId

    private suspend fun safeLookup(ids: List<String>): Map<String, String> = try {
        lookup(ids)
    } catch (e: Exception) {
        Timber.w(e, "Could not look up the moves of ${ids.size} ids")
        emptyMap()
    }

    companion object {
        /** the limit of the server for `GET /moved/` */
        const val MAX_IDS_PER_LOOKUP = 200
    }
}
