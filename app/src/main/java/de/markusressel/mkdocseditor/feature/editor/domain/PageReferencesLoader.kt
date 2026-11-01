package de.markusressel.mkdocseditor.feature.editor.domain

import com.github.ajalt.timberkt.Timber
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferences
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PageReferencesError
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.reference.BacklinksModel
import de.markusressel.mkdocsrestclient.reference.DocumentReferencesModel
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.reference.ReferenceModel
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Loads what the references panel shows. Both lists load at the same time, each one shows up
 * when it is there.
 */
internal class PageReferencesLoader(
    private val getReferences: suspend (documentId: String) -> Result<DocumentReferencesModel, FuelError>,
    private val getBacklinks: suspend (documentId: String) -> Result<BacklinksModel, FuelError>,
) {
    @Inject
    constructor(restClient: IMkDocsRestClient) : this(
        restClient::getDocumentReferences,
        { documentId -> restClient.getBacklinks(ItemType.DOCUMENT, documentId) },
    )

    /** @return a change of the panel data for each list that finished loading */
    fun load(documentId: String): Flow<(PageReferences) -> PageReferences> = channelFlow {
        launch {
            getReferences(documentId).fold(
                success = { result -> send { it.copy(references = result.references, referencesError = null) } },
                failure = { error ->
                    Timber.w(error) { "Could not load the references" }
                    send { it.copy(referencesError = error.toPageReferencesError()) }
                },
            )
        }
        launch {
            getBacklinks(documentId).fold(
                success = { result -> send { it.copy(backlinks = result.backlinks, backlinksError = null) } },
                failure = { error ->
                    Timber.w(error) { "Could not load the backlinks" }
                    send { it.copy(backlinksError = error.toPageReferencesError()) }
                },
            )
        }
    }

    /** older backends have no reference index; otherwise the server is not reachable */
    private fun FuelError.toPageReferencesError() =
        if (isUnsupportedEndpoint()) PageReferencesError.UNSUPPORTED else PageReferencesError.OFFLINE
}

/**
 * The state after a reference to a moved item was fixed: the backend sees the edit through the
 * sync, this shows the fixed state right away
 */
internal fun PageReferences.withFixedReference(reference: ReferenceModel, destination: String) = copy(
    references = references?.map {
        if (it == reference) {
            it.copy(destination = destination, status = "ok", target = it.movedTo, reason = null, path = null, movedTo = null, suggestedDestination = null)
        } else {
            it
        }
    }
)
