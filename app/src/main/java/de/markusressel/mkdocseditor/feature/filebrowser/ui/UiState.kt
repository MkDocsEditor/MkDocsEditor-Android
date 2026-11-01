package de.markusressel.mkdocseditor.feature.filebrowser.ui

import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveOutcome
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.SectionItem
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.MoveResultModel

sealed interface DialogState {
    data class CreateDocument(
        val sectionId: String,
        val initialDocumentName: String
    ) : DialogState

    data class CreateSection(
        val parentSectionId: String,
        val initialSectionName: String
    ) : DialogState

    /** what can be done with an item: rename, move, see what links to it, delete */
    data class ItemActions(val item: WikiItem) : DialogState

    /**
     * Moving or renaming an item. The user picks a target (or a new name), then sees a preview
     * of everything the backend would change (a dry run) before applying it.
     *
     * @param tree the wiki tree to pick the target folder from, null while it loads
     * @param renameOnly true to rename in place (no folder picker)
     * @param preview the dry run, null while picking the target
     * @param working true while a request runs
     * @param error the error of the last request
     */
    data class Move(
        val item: WikiItem,
        val renameOnly: Boolean,
        val tree: WikiTree? = null,
        val preview: MovePreview? = null,
        val working: Boolean = false,
        val error: MoveOutcome.Failed? = null,
    ) : DialogState

    /**
     * The documents that link to an item; [backlinks] is null while loading
     *
     * @param unsupported the backend is too old to know backlinks
     */
    data class Backlinks(
        val item: WikiItem,
        val backlinks: List<BacklinkModel>? = null,
        val error: String? = null,
        val unsupported: Boolean = false,
    ) : DialogState

    /**
     * @param usedBy how many references to the item exist, null while unknown
     * @param usedByUnavailable the count could not be loaded (older backend, no connection)
     */
    data class DeleteConfirmation(
        val item: WikiItem,
        val usedBy: Int? = null,
        val usedByUnavailable: Boolean = false,
    ) : DialogState
}

/**
 * A dry run of a move
 *
 * @param parentId the target section, null to rename in place
 * @param name the new name, null to keep it
 */
data class MovePreview(
    val parentId: String?,
    val name: String?,
    val result: MoveResultModel,
)

internal data class UiState(
    val currentDialogState: DialogState? = null,

    val fabConfig: FabConfig<FileBrowserFabId> = CreateItemsFabConfig,

    val isLoading: Boolean = false,
    val error: String? = null,

    val canGoUp: Boolean = false,

    val currentSearchFilter: String = "",
    val isSearchExpanded: Boolean = false,

    val currentSectionPath: List<SectionItem> = listOf(),

    val listItems: List<Any> = emptyList(),

    val currentSearchResults: List<SearchResultItem> = emptyList(),

    /** the selected backend, when its server rejected the session; the cached data stays visible */
    val loginRequiredBackendId: Long? = null,
)

sealed class FileBrowserFabId {
    data object FAB : FileBrowserFabId()
    data object CreateDocument : FileBrowserFabId()
    data object CreateSection : FileBrowserFabId()
    data object UploadResource : FileBrowserFabId()
}

val CreateItemsFabConfig = FabConfig(
    right = listOf(
        FabConfig.Fab(
            id = FileBrowserFabId.FAB,
            description = R.string.create,
            icon = MaterialDesignIconic.Icon.gmi_plus,
        ),
        FabConfig.Fab(
            id = FileBrowserFabId.UploadResource,
            description = R.string.upload_resource,
            icon = MaterialDesignIconic.Icon.gmi_attachment_alt,
        ),
        FabConfig.Fab(
            id = FileBrowserFabId.CreateDocument,
            description = R.string.create_document,
            icon = MaterialDesignIconic.Icon.gmi_file_add,
        ),
        FabConfig.Fab(
            id = FileBrowserFabId.CreateSection,
            description = R.string.create_section,
            icon = MaterialDesignIconic.Icon.gmi_folder,
        )
    )
)
