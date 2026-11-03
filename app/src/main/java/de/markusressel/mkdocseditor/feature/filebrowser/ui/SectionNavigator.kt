package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.transform

/**
 * Which folder is open and the way to it. The database decides the way, so the path follows a rename and a folder that
 * is gone, also when that happened on another device.
 *
 * @param initialSectionId the folder to start in
 * @param pathTo the sections from the root down to the given one, empty if it does not exist
 * @param sectionChanges has a value at the start and whenever the sections in the database change
 */
internal class SectionNavigator(
    initialSectionId: String,
    private val pathTo: suspend (String) -> List<SectionItem>,
    private val sectionChanges: (String) -> Flow<Unit>,
) {
    private val _currentSectionId = MutableStateFlow(initialSectionId)
    val currentSectionId = _currentSectionId.asStateFlow()

    private var lastPath: List<SectionItem> = listOf(ROOT_ITEM)

    /** The way to the open folder, starting at the root. When the folder is gone, the nearest parent opens. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val path: Flow<List<SectionItem>> = _currentSectionId.flatMapLatest { sectionId ->
        sectionChanges(sectionId).transform {
            val path = rootedPathTo(sectionId)
            if (path == null) {
                _currentSectionId.value = nearestExistingSection()
            } else {
                lastPath = path
                emit(path)
            }
        }
    }

    fun open(sectionId: String) {
        _currentSectionId.value = sectionId
    }

    /** @return false if the root is already open */
    suspend fun goUp(): Boolean {
        val path = rootedPathTo(currentSectionId.value) ?: lastPath
        val parent = path.getOrNull(path.lastIndex - 1) ?: return false
        _currentSectionId.value = parent.id
        return true
    }

    /** @return null if the section does not exist; the root always exists, even before the first sync */
    private suspend fun rootedPathTo(sectionId: String): List<SectionItem>? {
        if (sectionId == ROOT_SECTION_ID) return listOf(ROOT_ITEM)
        val path = pathTo(sectionId)
        if (path.isEmpty()) return null
        return listOf(ROOT_ITEM) + path.filter { it.id != ROOT_SECTION_ID }
    }

    private suspend fun nearestExistingSection(): String =
        lastPath.asReversed().firstOrNull { rootedPathTo(it.id) != null }?.id ?: ROOT_SECTION_ID

    private companion object {
        val ROOT_ITEM = SectionItem(ROOT_SECTION_ID, "/")
    }
}
