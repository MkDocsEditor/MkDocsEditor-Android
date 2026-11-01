package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import com.github.kittinunf.fuel.core.FuelError
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.reference.ApiErrorModel
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.reference.isUnsupportedEndpoint
import de.markusressel.mkdocsrestclient.reference.MoveRequestModel
import de.markusressel.mkdocsrestclient.reference.MoveResultModel
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The result of a move or its preview
 */
sealed interface MoveOutcome {
    data class Done(val result: MoveResultModel) : MoveOutcome

    /**
     * @param reason `details.reason` of the backend: `exists`, `invalid-name`, `blacklisted`,
     * `into-itself`, `root`, `no-change`; [UNSUPPORTED] when the backend is too old for
     * `POST /move/`; null for other errors
     */
    data class Failed(val status: Int?, val reason: String?, val message: String) : MoveOutcome {
        companion object {
            const val UNSUPPORTED = "unsupported"
        }
    }
}

/**
 * Moves or renames an item with `POST /move/`, which updates every reference to it, or
 * previews that (`dryRun`). After a real move, the local copies of moved documents follow
 * right away (docs/references.md section 7 of the backend).
 */
@Singleton
internal class MoveItemUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val localDocumentStore: LocalDocumentStore,
) {
    /**
     * @param parentId the target section, null to rename in place
     * @param name the new name (documents without `.md`), null to keep it
     */
    suspend operator fun invoke(item: WikiItem, parentId: String?, name: String?, dryRun: Boolean): MoveOutcome {
        val request = MoveRequestModel(
            type = item.type,
            id = item.id,
            parent = parentId,
            name = name,
            dryRun = dryRun,
        )
        return restClient.move(request).fold(
            success = { result ->
                if (!result.dryRun) followWithLocalCopies(result)
                MoveOutcome.Done(result)
            },
            failure = { error -> failure(error) },
        )
    }

    private suspend fun followWithLocalCopies(result: MoveResultModel) = withContext(Dispatchers.IO) {
        result.moved.filter { it.type == ItemType.DOCUMENT && it.oldId != it.newId }.forEach {
            try {
                localDocumentStore.move(it.oldId, it.newId)
            } catch (e: Exception) {
                // the copy stays under the old id; the redirect lookups find it later
                Timber.e(e, "Could not re-key the local copy of ${it.oldId}")
            }
        }
    }

    companion object {
        /** the backend's error (status, `details.reason`, message) */
        fun failure(error: FuelError): MoveOutcome.Failed {
            val status = error.response.statusCode.takeIf { it > 0 }
            val body = ApiErrorModel.parse(error.response.data)
            return MoveOutcome.Failed(
                status = status,
                reason = if (error.isUnsupportedEndpoint()) MoveOutcome.Failed.UNSUPPORTED else body?.details?.reason?.takeIf { it.isNotEmpty() },
                message = body?.message?.takeIf { it.isNotEmpty() } ?: error.message ?: error.toString(),
            )
        }
    }
}

/** the type of an item as the backend names it */
val WikiItem.type: String
    get() = when (this) {
        is WikiItem.Section -> ItemType.SECTION
        is WikiItem.Document -> ItemType.DOCUMENT
        is WikiItem.Resource -> ItemType.RESOURCE
    }
