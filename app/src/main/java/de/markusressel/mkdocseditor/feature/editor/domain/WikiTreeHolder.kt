package de.markusressel.mkdocseditor.feature.editor.domain

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import org.mobilenativefoundation.store.store5.impl.extensions.get
import javax.inject.Inject

/**
 * The wiki tree of the open document, for links and images in the preview and for the pickers
 */
internal class WikiTreeHolder(
    private val cached: suspend () -> SectionData,
    private val fresh: suspend () -> SectionData,
) {
    @Inject
    constructor(dataRepository: DataRepository) : this(
        { dataRepository.sectionStore.get(ROOT_SECTION_ID) },
        { dataRepository.sectionStore.fresh(ROOT_SECTION_ID) },
    )

    private val _tree = MutableStateFlow<WikiTree?>(null)
    val tree = _tree.asStateFlow()

    /**
     * Loads the wiki tree (from the offline cache if the server is not reachable)
     *
     * @return the tree, or null if there is none
     */
    suspend fun load(documentId: String): WikiTree? {
        val root = try {
            cached()
        } catch (ex: Exception) {
            Timber.w(ex) { "Could not load the wiki tree" }
            return null
        }
        val tree = WikiTree(root)
        // the cached tree can be older than the document (new or moved)
        if (tree.documentById(documentId) == null) return refresh()
        _tree.value = tree
        return tree
    }

    /**
     * Loads the wiki tree from the server, after uploads and moves
     *
     * @return the tree, or null if it could not be loaded
     */
    suspend fun refresh(): WikiTree? {
        val root = try {
            fresh()
        } catch (ex: Exception) {
            Timber.w(ex) { "Could not refresh the wiki tree" }
            return null
        }
        return WikiTree(root).also { _tree.value = it }
    }
}
