package de.markusressel.mkdocsrestclient.reference

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Method
import com.github.kittinunf.result.Result
import de.markusressel.mkdocsrestclient.RequestManager

class ReferenceApiImpl(
    private val requestManager: RequestManager,
) : ReferenceApi {

    override suspend fun getDocumentReferences(documentId: String): Result<DocumentReferencesModel, FuelError> =
        requestManager.doRequest(url = "/document/$documentId/references/", method = Method.GET)

    override suspend fun getBacklinks(type: String, id: String): Result<BacklinksModel, FuelError> =
        requestManager.doRequest(url = "/$type/$id/backlinks/", method = Method.GET)

    override suspend fun getLink(fromDocumentId: String, toId: String, anchor: String?): Result<LinkResultModel, FuelError> =
        requestManager.doRequest(
            url = "/link/",
            urlParameters = listOfNotNull("from" to fromDocumentId, "to" to toId, anchor?.let { "anchor" to it }),
            method = Method.GET,
        )

    override suspend fun move(request: MoveRequestModel): Result<MoveResultModel, FuelError> =
        requestManager.doJsonRequest(
            url = "/move/",
            method = Method.POST,
            jsonData = request,
            // a move rewrites every document that links to the item
            timeout = MOVE_TIMEOUT,
        )

    override suspend fun getMoved(oldId: String): Result<MovedModel, FuelError> =
        requestManager.doRequest(url = "/moved/$oldId/", method = Method.GET)

    private companion object {
        const val MOVE_TIMEOUT = 60_000
    }
}
