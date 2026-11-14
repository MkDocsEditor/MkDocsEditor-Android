package de.markusressel.mkdocseditor.feature.history.domain

import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.history.ChangeAuthorModel
import de.markusressel.mkdocsrestclient.history.DocumentChangesModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import timber.log.Timber
import javax.inject.Inject

/**
 * What changed in a page since the account last looked at it (`GET /document/{id}/changes/`), and marking it as seen.
 * The server keeps the mark, so all devices and the web client agree. A server without it tells nothing.
 */
internal class SeenChangesUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val backends: AuthBackends,
    private val authRepository: AuthRepository,
) {

    /**
     * The changes to tell about when the page opens. The page counts as seen as soon as they are shown, with the token
     * of this answer, so an edit that arrives while reading stays news.
     *
     * @return the changes with only the authors that are news, null if there is nothing to tell: the page was never
     * seen, nothing changed, only the account itself changed it, or the server can't say
     */
    suspend fun open(documentId: String): DocumentChangesModel? {
        val changes = changes(documentId) ?: return null
        markSeen(documentId, changes.seenToken)
        if (changes.since == null || changes.hunks.isEmpty()) return null
        val own = ownUsername()
        val others = changes.authors.filter { it.isNewsFor(own) }
        return if (others.isEmpty()) null else changes.copy(authors = others)
    }

    /** The page as it is now counts as seen, when the account leaves it: its own edits are no news later. */
    suspend fun leave(documentId: String) {
        changes(documentId)?.let { markSeen(documentId, it.seenToken) }
    }

    private suspend fun changes(documentId: String): DocumentChangesModel? =
        when (val result = restClient.getDocumentChanges(documentId)) {
            is Result.Success -> result.value
            is Result.Failure -> {
                if (!result.error.isUnsupportedEndpoint()) Timber.w(result.error, "changes of %s", documentId)
                null
            }
        }

    /** a token the document lost in the meantime (409) needs no retry: the next visit asks again */
    private suspend fun markSeen(documentId: String, seenToken: String) {
        val result = restClient.putDocumentSeen(documentId, seenToken)
        if (result is Result.Failure && !result.error.isUnsupportedEndpoint()) {
            Timber.w(result.error, "seen of %s", documentId)
        }
    }

    private suspend fun ownUsername(): String? = backends.selected()?.let { authRepository.session(it.id)?.username }
}

/** someone else changed the page, or an agent of the account itself did */
internal fun ChangeAuthorModel.isNewsFor(ownUsername: String?): Boolean =
    agents.isNotEmpty() || ownUsername == null || username != ownUsername
