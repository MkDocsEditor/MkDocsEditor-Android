package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import kotlinx.coroutines.CompletableDeferred

/**
 * Watches a connection that exists only to bring the unsynced changes of a local copy to the
 * server. A connection that fails for another reason leaves no result: the manager tries again,
 * and the caller decides how long to wait.
 */
class UnsyncedUploadWatcher : AutomergeDocumentSyncManager.Listener {

    enum class Result {
        /** the server confirmed the changes */
        Uploaded,

        /** the server will not take them: another try does not help, the copy keeps them */
        Refused,

        /** the session of the account is gone, nothing can be uploaded until the next sign-in */
        SignedOut,
    }

    private val result = CompletableDeferred<Result>()
    private var forbidden = 0

    suspend fun await(): Result = result.await()

    override fun onUnsyncedChangesChanged(unsynced: Boolean) {
        if (!unsynced) result.complete(Result.Uploaded)
    }

    override fun onReadOnlyChanged(readOnly: Boolean) {
        if (readOnly) result.complete(Result.Refused)
    }

    override fun onDocumentDeleted() {
        result.complete(Result.Refused)
    }

    override fun onConnectionStatusChanged(connected: Boolean, closeCode: Int?, throwable: Throwable?) {
        when (closeCode) {
            HTTP_UNAUTHORIZED, CloseCode.UNAUTHORIZED -> result.complete(Result.SignedOut)
            HTTP_FORBIDDEN, HTTP_NOT_FOUND, CloseCode.NOT_FOUND, CloseCode.PROTOCOL_ERROR -> result.complete(Result.Refused)
            // the first one may be a changed role, which the manager answers with one reconnect
            CloseCode.FORBIDDEN -> if (++forbidden > 1) result.complete(Result.Refused)
        }
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
