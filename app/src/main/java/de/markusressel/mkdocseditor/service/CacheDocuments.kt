package de.markusressel.mkdocseditor.service

import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result

private const val HTTP_UNAUTHORIZED = 401

/**
 * Fetches and stores the given documents. A failed document is logged in one line and skipped.
 * The first 401 ends the run: the session is the same for all documents, so the remaining ones
 * would only fail the same way, and the sign-in prompt is already triggered by the request.
 *
 * @return false if the run was ended early
 */
internal suspend fun cacheDocuments(
    documentIds: List<String>,
    getContent: suspend (String) -> Result<String, FuelError>,
    store: (documentId: String, text: String) -> Unit,
): Boolean {
    documentIds.forEach { documentId ->
        when (val result = getContent(documentId)) {
            is Result.Success -> try {
                store(documentId, result.value)
                Timber.d { "Offline cache sync finished successfully for documentId: $documentId" }
            } catch (e: Exception) {
                Timber.e(e) { "Offline cache sync could not store documentId: $documentId" }
            }

            is Result.Failure -> {
                val status = result.error.response.statusCode
                if (status == HTTP_UNAUTHORIZED) {
                    Timber.w { "Offline cache sync stopped: not signed in (HTTP 401)" }
                    return false
                }
                Timber.w { "Offline cache sync failed for documentId: $documentId (HTTP $status): ${result.error.message}" }
            }
        }
    }
    return true
}
