package de.markusressel.mkdocsrestclient.sync.automerge

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Follows moves of documents for the local copies. Document ids derive from paths, so a move
 * on the server changes the id. A copy stored under the old id, maybe with edits made offline,
 * is re-keyed to the new id before the document is opened again (docs/references.md section 7
 * of the backend). A connected session learns the new id from `document-renamed` instead.
 *
 * @param lookup asks the server where the document with an old id is now
 */
class LocalCopyRedirects(
    private val store: LocalDocumentStore,
    private val lookup: suspend (oldId: String) -> Lookup,
) {

    /** the answer of the server for an old id */
    sealed interface Lookup {
        /** the document moved and has this id now */
        data class Moved(val newId: String) : Lookup

        /** the id did not move: it is current, or the document is gone */
        data object NotMoved : Lookup

        /** no answer (offline, server error) */
        data object Unknown : Lookup
    }

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
        val answers = coroutineScope {
            toLookUp.map { id -> async { id to safeLookup(id) } }.map { it.await() }
        }
        buildMap {
            answers.forEach { (oldId, answer) ->
                if (answer is Lookup.Moved && answer.newId != oldId) {
                    if (oldId in ids) {
                        store.move(oldId, answer.newId)
                        Timber.i("Local copy of $oldId follows the move to ${answer.newId}")
                    }
                    put(oldId, answer.newId)
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

    private suspend fun safeLookup(id: String): Lookup = try {
        lookup(id)
    } catch (e: Exception) {
        Timber.w(e, "Could not look up the moves of $id")
        Lookup.Unknown
    }
}
