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

    override suspend fun restoreDocumentRevision(documentId: String, revision: String): Result<String, FuelError> =
        requestManager.doRequest(
            url = "/document/$documentId/history/$revision/restore/",
            method = Method.POST,
            kind = RequestKind.LongAction,
        )
}
