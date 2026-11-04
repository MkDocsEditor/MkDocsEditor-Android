package de.markusressel.mkdocsrestclient.reference

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result

/**
 * References between items, moving items and redirects of old ids
 * (docs/references.md of the backend)
 */
interface ReferenceApi {

    /** the outgoing references of a document, in text order */
    suspend fun getDocumentReferences(documentId: String): Result<DocumentReferencesModel, FuelError>

    /**
     * The references to an item ("used by")
     *
     * @param type [ItemType]
     */
    suspend fun getBacklinks(type: String, id: String): Result<BacklinksModel, FuelError>

    /** who wrote which part of a document */
    suspend fun getAuthorship(documentId: String): Result<AuthorshipModel, FuelError>

    /** the link destination from a document to an item, computed by the backend */
    suspend fun getLink(fromDocumentId: String, toId: String, anchor: String? = null): Result<LinkResultModel, FuelError>

    /** moves or renames an item and updates the references to it, or previews that with `dryRun` */
    suspend fun move(request: MoveRequestModel): Result<MoveResultModel, FuelError>

    /**
     * Where the items with the given old ids are now. Ids that did not move (current ids too) and
     * items that are gone are not part of the answer.
     *
     * @param oldIds at most 200 ids, the server refuses more
     */
    suspend fun getMoved(oldIds: List<String>): Result<MovedListModel, FuelError>
}
