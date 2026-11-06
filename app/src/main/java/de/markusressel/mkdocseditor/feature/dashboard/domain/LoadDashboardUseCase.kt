package de.markusressel.mkdocseditor.feature.dashboard.domain

import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.dashboard.FeedPageModel
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import timber.log.Timber
import javax.inject.Inject

/**
 * What the server has for the dashboard
 *
 * @param pins null when the server has no pins yet
 * @param feed null when the server has no feed yet
 */
internal data class DashboardContent(
    val pins: PinsModel?,
    val feed: FeedPageModel?,
)

/**
 * Loads the pins and the newest page of the feed. Pinned pages are kept offline, so their text is stored too.
 */
internal class LoadDashboardUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val dataRepository: DataRepository,
) {
    /** @throws Exception when the server could not be reached */
    suspend operator fun invoke(): DashboardContent = coroutineScope {
        val pins = async { restClient.getPins().orNullIfUnsupported() }
        val feed = async { restClient.getFeed().orNullIfUnsupported() }
        DashboardContent(pins = pins.await(), feed = feed.await())
    }

    /**
     * Stores the text of pinned pages that have no local copy yet. A failure stays quiet: the background sync stores
     * them later.
     */
    suspend fun keepOffline(pins: PinsModel, hasCopy: (documentId: String) -> Boolean) {
        (pins.family + pins.personal).map { it.documentId }.distinct().filterNot(hasCopy).forEach { documentId ->
            try {
                restClient.getDocumentContent(documentId).get().let { dataRepository.updateDocumentContentInCache(documentId, it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Could not store a pinned page offline")
            }
        }
    }

    /** @return older entries of the feed */
    suspend fun olderFeed(before: String): FeedPageModel = restClient.getFeed(before = before).get()
}

/** an older server without the endpoint answers null */
private fun <T : Any> Result<T, com.github.kittinunf.fuel.core.FuelError>.orNullIfUnsupported(): T? = when (this) {
    is Result.Success -> value
    is Result.Failure -> if (error.isUnsupportedEndpoint()) null else throw error
}
