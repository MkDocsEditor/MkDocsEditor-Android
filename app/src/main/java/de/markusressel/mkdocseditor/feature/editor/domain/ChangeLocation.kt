package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocsrestclient.dashboard.FeedSummaryModel
import java.io.Serializable

/**
 * Where an edit of the feed or of a notification was, for the page to open there. Serializable, because it is part of
 * the arguments of a screen and of the intent of a notification.
 *
 * @param headings the headings of the changed sections, for an entry without [ranges]
 * @param textHash identifies the text after the edit, for which [ranges] are exact
 * @param ranges the changed lines, null for an entry from before the backend sent them, empty when only the newline at
 * the end changed
 */
data class ChangeLocation(
    val headings: List<String>,
    val textHash: String? = null,
    val ranges: List<ChangedLines>? = null,
) : Serializable

/**
 * Lines an edit added or changed, or with [count] 0 the place of removed lines, as the backend sends them (see
 * `FeedRangeModel`)
 *
 * @param line 1-based
 */
data class ChangedLines(
    val line: Int,
    val count: Int,
    val heading: String?,
    val headingOccurrence: Int,
    val offset: Int,
) : Serializable

/** where the edit of this summary was, with [headings] for an older backend */
fun FeedSummaryModel.changeLocation(headings: List<String> = this.headings) = ChangeLocation(
    headings = headings,
    textHash = textHash,
    ranges = ranges?.map { ChangedLines(it.line, it.count, it.heading, it.headingOccurrence, it.offset) },
)
