package de.markusressel.mkdocsrestclient.history

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.util.Date

/**
 * An author of a commit. The backend sends empty strings, not null, for an author who is not an account, like the
 * initial import.
 *
 * @param username the current username of the account
 * @param displayName the current name of the account, like in the feed; null from a backend before it
 * @param avatar the version of the profile picture, null without one
 */
@JsonClass(generateAdapter = true)
data class HistoryAuthorModel(
    val name: String,
    val email: String,
    val username: String = "",
    val accountId: String = "",
    val displayName: String? = null,
    val color: String? = null,
    val avatar: String? = null,
) {
    val isAccount: Boolean get() = accountId.isNotEmpty()
}

/**
 * A commit that touched a document, from `GET /document/{id}/history/`
 *
 * @param revision the full commit hash
 * @param path the path of the file in that commit, which differs from today's after a rename
 * @param appTokens the names of the app tokens (AI agents) the changes were made with
 */
@JsonClass(generateAdapter = true)
data class HistoryEntryModel(
    val revision: String,
    val time: Date,
    val author: HistoryAuthorModel,
    val coAuthors: List<HistoryAuthorModel> = emptyList(),
    val message: String,
    val path: String,
    val appTokens: List<String> = emptyList(),
    /** the AI agents the changes were made with, with the account each one belongs to */
    val agents: List<HistoryAgentModel> = emptyList(),
    /** null from a backend before it */
    val summary: HistorySummaryModel? = null,
)

/** An AI agent that changed a document, with an app token of [accountId] */
@JsonClass(generateAdapter = true)
data class HistoryAgentModel(
    /** the name of the app token */
    val name: String,
    val accountId: String = "",
)

/** @param headings the headings of the changed sections, as in the feed */
@JsonClass(generateAdapter = true)
data class HistorySummaryModel(
    val linesAdded: Int = 0,
    val linesRemoved: Int = 0,
    val headings: List<String> = emptyList(),
)

/** `GET /document/{id}/history/diff/?format=json` */
@JsonClass(generateAdapter = true)
data class HistoryDiffModel(
    val from: String,
    val to: String,
    val hunks: List<DiffHunkModel> = emptyList(),
)

/**
 * Changed lines with up to 3 unchanged ones around them
 *
 * @param heading the heading of the section, as in the feed: computed on the new text, and for lines that were only
 * removed the heading above where they were. Null before the first heading.
 * @param headingOccurrence which of the headings with this text, from 1
 * @param oldStart the first line in the old text, from 1
 * @param offset [newStart] minus the line of [heading], or [newStart] - 1 without one, as in the feed; null from a
 * backend before it
 */
@JsonClass(generateAdapter = true)
data class DiffHunkModel(
    val heading: String? = null,
    val headingOccurrence: Int = 0,
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val lines: List<DiffLineModel> = emptyList(),
    val offset: Int? = null,
)

@JsonClass(generateAdapter = true)
data class DiffLineModel(
    val op: DiffOp,
    val text: String,
)

enum class DiffOp {
    @Json(name = " ")
    CONTEXT,

    @Json(name = "-")
    REMOVED,

    @Json(name = "+")
    ADDED,
}

/**
 * The error body of a revert that changed nothing
 *
 * @param code `revert-conflict` when later changes touched the same lines, `nothing-to-revert` when the change is gone
 */
@JsonClass(generateAdapter = true)
data class RevertErrorModel(
    val code: String? = null,
    val details: RevertConflictModel? = null,
) {
    companion object {
        const val CONFLICT = "revert-conflict"
        const val NOTHING_TO_REVERT = "nothing-to-revert"

        private val adapter by lazy { Moshi.Builder().build().adapter(RevertErrorModel::class.java) }

        /** the error in a response body, null when the body is not one */
        fun parse(body: ByteArray): RevertErrorModel? = try {
            adapter.fromJson(String(body, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}

/** @param headings the distinct headings of the sections where later changes are in the way */
@JsonClass(generateAdapter = true)
data class RevertConflictModel(
    val headings: List<String?> = emptyList(),
)
