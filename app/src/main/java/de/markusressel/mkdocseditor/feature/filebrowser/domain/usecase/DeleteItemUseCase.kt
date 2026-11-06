package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import javax.inject.Inject
import javax.inject.Singleton

internal enum class DeleteOutcome {
    Deleted,

    /** someone has the page, or a page in the folder, open right now; [DeleteItemUseCase] with `force` deletes it anyway */
    InUse,
}

/**
 * Deletes a page, a folder or a file
 */
@Singleton
internal class DeleteItemUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    /**
     * @param force also while clients are connected to it
     * @throws FuelError for every other failure
     */
    suspend operator fun invoke(item: WikiItem, force: Boolean): DeleteOutcome {
        val result = when (item) {
            is WikiItem.Document -> restClient.deleteDocument(item.id, force)
            is WikiItem.Section -> restClient.deleteSection(item.id, force)
            is WikiItem.Resource -> restClient.deleteResource(item.id)
        }
        return when (result) {
            is Result.Success -> DeleteOutcome.Deleted
            is Result.Failure -> if (result.error.response.statusCode == HTTP_CONFLICT) DeleteOutcome.InUse else throw result.error
        }
    }

    private companion object {
        const val HTTP_CONFLICT = 409
    }
}
