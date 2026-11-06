package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.toFolderContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class ObserveStoredFolderUseCase @Inject constructor(
    private val dataRepository: DataRepository,
) {
    /** The stored content of a folder, null while there is none. It never loads, so it can run for a folder that is not open. */
    operator fun invoke(sectionId: String): Flow<FolderContent?> =
        dataRepository.observeStoredSection(sectionId).map { it?.toFolderContent() }
}
