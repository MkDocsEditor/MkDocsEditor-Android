package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.ui.FileBrowserViewModel.Companion.ROOT_SECTION_ID
import kotlinx.coroutines.CancellationException
import org.mobilenativefoundation.store.store5.impl.extensions.fresh
import org.mobilenativefoundation.store.store5.impl.extensions.get
import javax.inject.Inject

/** Loads the whole wiki tree, from the server if possible and from the cache otherwise */
internal class LoadWikiTreeUseCase(
    private val fresh: suspend () -> SectionData,
    private val cached: suspend () -> SectionData,
) {
    @Inject
    constructor(dataRepository: DataRepository) : this(
        fresh = { dataRepository.sectionStore.fresh(ROOT_SECTION_ID) },
        cached = { dataRepository.sectionStore.get(ROOT_SECTION_ID) },
    )

    /** @return the tree, null if there is none */
    suspend operator fun invoke(): WikiTree? {
        val root = try {
            fresh()
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex) { "Could not refresh the wiki tree" }
            try {
                cached()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (ex2: Exception) {
                Timber.e(ex2)
                return null
            }
        }
        return WikiTree(root)
    }
}
