package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The references to an item ("used by"), sorted by source path, line and column. For a
 * section: references to it, its index and everything inside it.
 */
@Singleton
internal class GetBacklinksUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(item: WikiItem): Result<List<BacklinkModel>> =
        restClient.getBacklinks(item.type, item.id).fold(
            success = { Result.success(it.backlinks) },
            failure = { Result.failure(it) },
        )
}
