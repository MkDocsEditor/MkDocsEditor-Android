package de.markusressel.mkdocsrestclient.dashboard

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.RequestKind
import de.markusressel.mkdocsrestclient.RequestManager

/**
 * Pinned pages and the feed of changes, for the dashboard (features `pins` and `feed` of the backend)
 */
interface DashboardApi {

    suspend fun getPins(): Result<PinsModel, FuelError>

    /** idempotent; family pins need an editor or admin, otherwise 403 */
    suspend fun pin(scope: PinScope, documentId: String): Result<PinsModel, FuelError>

    /** idempotent */
    suspend fun unpin(scope: PinScope, documentId: String): Result<PinsModel, FuelError>

    /**
     * @param before the cursor of [FeedPageModel.next], null for the newest page
     * @param limit 1 to 200
     */
    suspend fun getFeed(before: String? = null, limit: Int = 50): Result<FeedPageModel, FuelError>
}

class DashboardApiImpl(
    private val requestManager: RequestManager,
) : DashboardApi {

    override suspend fun getPins(): Result<PinsModel, FuelError> =
        requestManager.doRequest(url = "/pins/", method = Method.GET)

    override suspend fun pin(scope: PinScope, documentId: String): Result<PinsModel, FuelError> =
        requestManager.doRequest(url = "/pins/${scope.path}/$documentId/", method = Method.PUT, kind = RequestKind.LongAction)

    override suspend fun unpin(scope: PinScope, documentId: String): Result<PinsModel, FuelError> =
        requestManager.doRequest(url = "/pins/${scope.path}/$documentId/", method = Method.DELETE, kind = RequestKind.LongAction)

    override suspend fun getFeed(before: String?, limit: Int): Result<FeedPageModel, FuelError> =
        requestManager.doRequest(
            url = "/feed/",
            urlParameters = listOfNotNull("limit" to limit, before?.let { "before" to it }),
            method = Method.GET,
        )
}
