package de.markusressel.mkdocsrestclient.dashboard

import com.squareup.moshi.JsonClass
import de.markusressel.mkdocsrestclient.moshi
import java.util.Date

/**
 * Who did something, with the current name, color and picture of the account
 *
 * @param accountId null for `:disk` ("External change") and `:server`
 * @param avatar the version of the picture, null without one
 */
@JsonClass(generateAdapter = true)
data class FeedAuthorModel(
    val accountId: String? = null,
    val username: String? = null,
    val displayName: String,
    val color: String,
    val avatar: String? = null,
)

/**
 * A pinned document
 *
 * @param path relative to the docs folder, like `guide/setup.md`
 * @param name the document name without `.md`, as in the tree
 * @param title the page name, see [de.markusressel.mkdocsrestclient.document.DocumentModel.title]; null from a backend
 * without `page-meta`
 */
@JsonClass(generateAdapter = true)
data class PinModel(
    val documentId: String,
    val path: String,
    val name: String,
    val pinnedBy: FeedAuthorModel,
    val pinnedAt: Date,
    val title: String? = null,
)

/**
 * Answer of `GET /pins/` and of every pin change, each list newest first
 */
@JsonClass(generateAdapter = true)
data class PinsModel(
    val family: List<PinModel> = emptyList(),
    val personal: List<PinModel> = emptyList(),
)

/**
 * What an edit changed
 *
 * @param headings up to 5 headings whose sections the edit touched, in document order
 */
@JsonClass(generateAdapter = true)
data class FeedSummaryModel(
    val linesAdded: Int = 0,
    val linesRemoved: Int = 0,
    val headings: List<String> = emptyList(),
)

/**
 * A change in the wiki. One editing session of a document is one entry.
 *
 * @param kind [FeedKind]
 * @param itemType `document` or `section`
 * @param documentId the current id, while the item still exists at [path]
 * @param title the page name of the document at [path], null without one or from a backend without `page-meta`
 * @param oldPath only for [FeedKind.MOVED]
 * @param summary only for [FeedKind.EDITED]
 */
@JsonClass(generateAdapter = true)
data class FeedEntryModel(
    val id: String,
    val time: Date,
    val kind: String,
    val itemType: String,
    val documentId: String? = null,
    val path: String,
    val oldPath: String? = null,
    val authors: List<FeedAuthorModel> = emptyList(),
    val summary: FeedSummaryModel? = null,
    val title: String? = null,
)

/**
 * A page of `GET /feed/`, newest first
 *
 * @param next the cursor of the next older page, null at the end
 */
@JsonClass(generateAdapter = true)
data class FeedPageModel(
    val entries: List<FeedEntryModel> = emptyList(),
    val next: String? = null,
)

object FeedKind {
    const val EDITED = "edited"
    const val CREATED = "created"
    const val MOVED = "moved"
    const val DELETED = "deleted"
}

/** whose pins: everyone's, set by editors and admins, or those of the account itself */
enum class PinScope(internal val path: String) {
    Family("family"),
    Personal("personal"),
}

/**
 * What the dashboard showed last, stored so that it opens at once and offline
 */
@JsonClass(generateAdapter = true)
data class DashboardSnapshotModel(
    val pins: PinsModel = PinsModel(),
    val feed: FeedPageModel = FeedPageModel(),
) {
    fun toJson(): String = adapter.toJson(this)

    companion object {
        private val adapter by lazy { moshi.adapter(DashboardSnapshotModel::class.java) }

        /** @return null when [json] is not a stored snapshot */
        fun parse(json: String): DashboardSnapshotModel? = try {
            adapter.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }
}
