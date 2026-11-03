package de.markusressel.mkdocseditor.service

import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private const val HTTP_UNAUTHORIZED = 401

internal enum class CacheOutcome {
    /** all documents are in the cache now */
    Complete,

    /** some documents could not be fetched or stored */
    SomeFailed,

    /** the server does not accept the session */
    NotSignedIn,
}

/**
 * Fetches and stores the given documents. A failed document is logged in one line and skipped.
 * The first 401 ends the run: the session is the same for all documents, so the remaining ones
 * would only fail the same way, and the sign-in prompt is already triggered by the request.
 *
 * Stops as soon as the coroutine is cancelled, for example when WorkManager takes back the work.
 */
internal suspend fun cacheDocuments(
    documentIds: List<String>,
    getContent: suspend (String) -> Result<String, FuelError>,
    store: (documentId: String, text: String) -> Unit,
): CacheOutcome {
    var outcome = CacheOutcome.Complete
    documentIds.forEach { documentId ->
        currentCoroutineContext().ensureActive()
        when (val result = getContent(documentId)) {
            is Result.Success -> try {
                store(documentId, result.value)
                Timber.d { "Offline cache sync finished successfully for documentId: $documentId" }
            } catch (e: Exception) {
                Timber.e(e) { "Offline cache sync could not store documentId: $documentId" }
                outcome = CacheOutcome.SomeFailed
            }

            is Result.Failure -> {
                val status = result.error.response.statusCode
                if (status == HTTP_UNAUTHORIZED) {
                    Timber.w { "Offline cache sync stopped: not signed in (HTTP 401)" }
                    return CacheOutcome.NotSignedIn
                }
                Timber.w { "Offline cache sync failed for documentId: $documentId (HTTP $status): ${result.error.message}" }
                outcome = CacheOutcome.SomeFailed
            }
        }
    }
    return outcome
}
