package de.markusressel.mkdocseditor.feature.history.domain

import androidx.annotation.StringRes
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.history.BlameBlockModel
import timber.log.Timber
import javax.inject.Inject

/**
 * Who last changed a line of a page and when, from the blame of the server (`GET /document/{id}/blame/`). It is asked
 * for each time, because the page changes while it is open.
 */
internal class LineAuthorUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    sealed interface Answer {
        /** the lines around [line] that were changed together */
        data class Found(val block: BlameBlockModel) : Answer

        /** the line is newer than the text the server knows, or nobody known wrote it */
        data object Unknown : Answer

        data class Failed(@param:StringRes val message: Int) : Answer
    }

    /** @param line 0-based, as in the editor */
    suspend operator fun invoke(documentId: String, line: Int): Answer =
        when (val result = restClient.getDocumentBlame(documentId)) {
            is Result.Success -> result.value.blockAt(line + 1)
                ?.takeIf { it.author != null }
                ?.let { Answer.Found(it) }
                ?: Answer.Unknown
            is Result.Failure -> {
                Timber.w(result.error, "blame of %s", documentId)
                Answer.Failed(result.error.userMessage())
            }
        }
}
