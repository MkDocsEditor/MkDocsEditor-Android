package de.markusressel.mkdocseditor.feature.browser.ui

import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.domain.usecase.SectionItem
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig

sealed interface DialogState {
    data class CreateDocument(
        val sectionId: String,
        val initialDocumentName: String
    ) : DialogState

    data class EditDocument(
        val documentId: String,
        val initialDocumentName: String
    ) : DialogState

    data class DeleteDocumentConfirmation(
        val documentId: String
    ) : DialogState

    data class CreateSection(
        val parentSectionId: String,
        val initialSectionName: String
    ) : DialogState

    data class EditSection(
        val sectionId: String,
        val initialSectionName: String
    ) : DialogState

    data class DeleteSectionConfirmation(
        val sectionId: String
    ) : DialogState


}

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

    val currentSearchResults: List<Any> = emptyList(),
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
