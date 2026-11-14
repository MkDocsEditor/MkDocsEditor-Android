package de.markusressel.mkdocsrestclient.history

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.map
import de.markusressel.mkdocsrestclient.RequestKind
import de.markusressel.mkdocsrestclient.RequestManager

/** Names the live text of a document in a diff */
const val REVISION_CURRENT = "current"

/**
 * The revision history of a document, from the git history of its file. A backend without `git.enabled` answers 404.
 */
interface HistoryApi {

    /**
     * @param limit 1 to 1000
     * @return the commits that touched the document, newest first
     */
    suspend fun getDocumentHistory(documentId: String, limit: Int = 100): Result<List<HistoryEntryModel>, FuelError>

    /** @return the text of the document at [revision] */
    suspend fun getDocumentRevisionContent(documentId: String, revision: String): Result<String, FuelError>

    /**
     * @param from a revision or [REVISION_CURRENT]
     * @param to a revision or [REVISION_CURRENT]
     * @return a unified diff, empty if both texts are equal
     */
    suspend fun getDocumentRevisionDiff(
        documentId: String,
        from: String,
        to: String = REVISION_CURRENT,
    ): Result<String, FuelError>

    /** The same as [getDocumentRevisionDiff], as hunks with the heading of each one */
    suspend fun getDocumentRevisionDiffHunks(
        documentId: String,
        from: String,
        to: String = REVISION_CURRENT,
    ): Result<HistoryDiffModel, FuelError>

    /**
     * @param since a seen token, null for the last look of the signed in account
     * @return what changed since then; a token the document no longer knows answers 409 `unknown-revision`
     */
    suspend fun getDocumentChanges(documentId: String, since: String? = null): Result<DocumentChangesModel, FuelError>

    /** Marks the text of [seenToken] as seen by the signed in account. A token the document no longer knows answers 409. */
    suspend fun putDocumentSeen(documentId: String, seenToken: String): Result<SeenModel, FuelError>

    /** Sets the live text to the one of [revision], as an edit of the signed in account. Editors and admins only. */
    suspend fun restoreDocumentRevision(documentId: String, revision: String): Result<String, FuelError>
}

class HistoryApiImpl(
    private val requestManager: RequestManager,
) : HistoryApi {

    override suspend fun getDocumentHistory(documentId: String, limit: Int): Result<List<HistoryEntryModel>, FuelError> =
        requestManager.doRequest<Array<HistoryEntryModel>>(
            url = "/document/$documentId/history/",
            urlParameters = listOf("limit" to limit),
            method = Method.GET,
        ).map { it.toList() }

    override suspend fun getDocumentRevisionContent(documentId: String, revision: String): Result<String, FuelError> =
        requestManager.doRequest(url = "/document/$documentId/history/$revision/content/", method = Method.GET)

    override suspend fun getDocumentRevisionDiff(documentId: String, from: String, to: String): Result<String, FuelError> =
        requestManager.doRequest(
            url = "/document/$documentId/history/diff/",
            urlParameters = listOf("from" to from, "to" to to),
            method = Method.GET,
        )

    override suspend fun getDocumentRevisionDiffHunks(
        documentId: String,
        from: String,
        to: String,
    ): Result<HistoryDiffModel, FuelError> =
        requestManager.doRequest(
            url = "/document/$documentId/history/diff/",
            urlParameters = listOf("from" to from, "to" to to, "format" to "json"),
            method = Method.GET,
        )

    override suspend fun getDocumentChanges(documentId: String, since: String?): Result<DocumentChangesModel, FuelError> =
        requestManager.doRequest(
            url = "/document/$documentId/changes/",
            urlParameters = listOfNotNull(since?.let { "since" to it }),
            method = Method.GET,
        )

    override suspend fun putDocumentSeen(documentId: String, seenToken: String): Result<SeenModel, FuelError> =
        requestManager.doJsonRequest(
            url = "/document/$documentId/seen/",
            method = Method.PUT,
            jsonData = SeenRequestModel(seenToken),
            kind = RequestKind.Quick,
        )

    override suspend fun restoreDocumentRevision(documentId: String, revision: String): Result<String, FuelError> =
        requestManager.doRequest(
            url = "/document/$documentId/history/$revision/restore/",
            method = Method.POST,
            kind = RequestKind.LongAction,
        )
}
