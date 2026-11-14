package de.markusressel.mkdocsrestclient.history

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import java.util.Date

/**
 * Who last changed each line of a document, from `GET /document/{id}/blame/`
 *
 * @param revision the revision of the text the blame belongs to, a seen token; it may be a little behind the live text
 * @param lines the line count of that text
 * @param blocks consecutive lines of the same commit, or pending lines of the same account and agent, in text order
 */
@JsonClass(generateAdapter = true)
data class BlameModel(
    val revision: String,
    val lines: Int,
    val blocks: List<BlameBlockModel> = emptyList(),
) {
    /** @param line 1-based */
    fun blockAt(line: Int): BlameBlockModel? = blocks.firstOrNull { line in it.start..it.end }
}

/**
 * @param start the first line, 1-based
 * @param end the last line, inclusive
 * @param pending lines not committed yet; they have no [revision], [time] or [message]
 * @param author the commit's author, for pending lines the one who wrote most of them; null if nobody known did
 * @param agents the names of the app tokens (AI agents) the lines were written with
 */
@JsonClass(generateAdapter = true)
data class BlameBlockModel(
    val start: Int,
    val end: Int,
    val pending: Boolean = false,
    val revision: String? = null,
    val time: Date? = null,
    val message: String? = null,
    val author: FeedAuthorModel? = null,
    val agents: List<String> = emptyList(),
)

/** The body of a section restore: the section of the heading at its [headingOccurrence], 1 for the first */
@JsonClass(generateAdapter = true)
data class SectionRestoreRequestModel(
    val heading: String,
    val headingOccurrence: Int,
)

/** The error body of a section restore that changed nothing */
@JsonClass(generateAdapter = true)
data class SectionRestoreErrorModel(
    val code: String? = null,
    val details: SectionNotFoundModel? = null,
) {
    companion object {
        const val SECTION_NOT_FOUND = "section-not-found"

        private val adapter by lazy { Moshi.Builder().build().adapter(SectionRestoreErrorModel::class.java) }

        /** the error in a response body, null when the body is not one */
        fun parse(body: ByteArray): SectionRestoreErrorModel? = try {
            adapter.fromJson(String(body, Charsets.UTF_8))
        } catch (e: Exception) {
            null
        }
    }
}

/** @param missingIn which text has no such section: [REVISION] or [CURRENT] */
@JsonClass(generateAdapter = true)
data class SectionNotFoundModel(
    @Json(name = "in") val missingIn: String? = null,
) {
    companion object {
        const val REVISION = "revision"
        const val CURRENT = "current"
    }
}
