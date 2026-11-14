package de.markusressel.mkdocsrestclient.history

import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * What changed in a document since the signed in account last looked, from `GET /document/{id}/changes/`
 *
 * @param since when the account last looked, null if it never did: then there are no hunks
 * @param seenToken the text this answer is about, to send to `PUT /document/{id}/seen/` once it was shown
 * @param hunks against the live text, empty if nothing changed
 */
@JsonClass(generateAdapter = true)
data class DocumentChangesModel(
    val since: SeenMarkModel? = null,
    val seenToken: String,
    val authors: List<ChangeAuthorModel> = emptyList(),
    val hunks: List<DiffHunkModel> = emptyList(),
    val summary: HistorySummaryModel = HistorySummaryModel(),
) {
    override fun toString() = "DocumentChangesModel(since=${since?.time}, authors=${authors.size}, hunks=${hunks.size})"
}

/** The last look of the account at a document */
@JsonClass(generateAdapter = true)
data class SeenMarkModel(
    val time: Date,
    val seenToken: String,
) {
    override fun toString() = "SeenMarkModel(time=$time)"
}

/**
 * Someone who changed the document, with the current name, color and picture of the account, like in the feed
 *
 * @param agents the names of the app tokens this account changed the document with
 */
@JsonClass(generateAdapter = true)
data class ChangeAuthorModel(
    val accountId: String? = null,
    val username: String? = null,
    val displayName: String,
    val color: String? = null,
    val avatar: String? = null,
    val agents: List<String> = emptyList(),
)

/** `PUT /document/{id}/seen/` */
@JsonClass(generateAdapter = true)
data class SeenRequestModel(
    val seenToken: String,
) {
    override fun toString() = "SeenRequestModel"
}

/** The answer of `PUT /document/{id}/seen/` */
@JsonClass(generateAdapter = true)
data class SeenModel(
    val seenAt: Date,
    val seenToken: String,
) {
    override fun toString() = "SeenModel(seenAt=$seenAt)"
}
