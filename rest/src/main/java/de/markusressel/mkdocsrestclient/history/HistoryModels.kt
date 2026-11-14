package de.markusressel.mkdocsrestclient.history

import com.squareup.moshi.JsonClass
import java.util.Date

/**
 * An author of a commit. The backend sends empty strings, not null, for an author who is not an account, like the
 * initial import.
 *
 * @param username the current username of the account
 */
@JsonClass(generateAdapter = true)
data class HistoryAuthorModel(
    val name: String,
    val email: String,
    val username: String = "",
    val accountId: String = "",
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
)
