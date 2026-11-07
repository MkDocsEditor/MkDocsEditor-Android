package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocsrestclient.reference.AuthorshipModel
import kotlinx.coroutines.delay

/**
 * Asks the backend who wrote what. An answer only fits the exact state of the text it describes, so it waits until
 * the text has settled. While someone types without a pause it asks after [maxWaitMs] anyway; an answer that no
 * longer fits is dropped, and the next attempt waits for the next settle or the next [maxWaitMs].
 *
 * Call [load] for every change of the text, and cancel the call before when a new change comes (`collectLatest`).
 *
 * @param fetch asks the backend, null if that failed
 * @param show shows an answer, false if it does not fit the text
 * @param now a monotonic clock in milliseconds
 */
internal class AuthorshipLoader(
    private val fetch: suspend (documentId: String) -> AuthorshipModel?,
    private val show: (AuthorshipModel) -> Boolean,
    private val now: () -> Long,
    private val settleMs: Long = AUTHORSHIP_SETTLE_MS,
    private val maxWaitMs: Long = AUTHORSHIP_MAX_WAIT_MS,
    private val attempts: Int = AUTHORSHIP_ATTEMPTS,
) {
    /** when the first change came that no request has asked about yet */
    private var waitingSince: Long? = null

    suspend fun load(documentId: String) {
        val since = waitingSince ?: now().also { waitingSince = it }
        // the first wait can be cut short by the maximum, a retry of an answer that did not fit waits the whole time
        delay((since + maxWaitMs - now()).coerceIn(0, settleMs))
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(settleMs)
            waitingSince = null
            val model = fetch(documentId) ?: return
            if (show(model)) return
        }
    }
}

/** how long the text has to stay as it is before asking who wrote it */
private const val AUTHORSHIP_SETTLE_MS = 2000L

/** how long to wait at most while someone keeps typing */
private const val AUTHORSHIP_MAX_WAIT_MS = 10_000L

/** how often to ask while the text stays as it is: the backend may not have the last changes yet */
private const val AUTHORSHIP_ATTEMPTS = 3
