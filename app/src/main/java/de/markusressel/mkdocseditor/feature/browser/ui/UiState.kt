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
        val sectionId: String,
        val initialDocumentName: String
    ) : DialogState

    data class CreateSection(
        val parentSectionId: String,
        val initialSectionName: String
    ) : DialogState

    data class EditSection(
        val sectionId: String,
        val initialSectionName: String
    ) : DialogState

}

internal data class UiState(
    val currentDialogState: DialogState? = null,

    val fabConfig: FabConfig = FabConfig(
        right = listOf(
            FabConfig.Fab(
                id = -1,
                description = R.string.create,
                icon = MaterialDesignIconic.Icon.gmi_plus,
            ),
            FabConfig.Fab(
                id = FAB_ID_CREATE_DOCUMENT,
                description = R.string.create_document,
                icon = MaterialDesignIconic.Icon.gmi_file_add,
            ),
            FabConfig.Fab(
                id = FAB_ID_CREATE_SECTION,
                description = R.string.create_section,
                icon = MaterialDesignIconic.Icon.gmi_folder,
            )
        )
    ),

    val isLoading: Boolean = false,
    val error: String? = null,

    val canGoUp: Boolean = false,

    val isSearchExpanded: Boolean = false,
    val currentSearchFilter: String = "",
    val isSearching: Boolean = false,

    val currentSectionPath: List<SectionItem> = listOf(),

    val listItems: List<Any> = emptyList()
)

const val FAB_ID_CREATE_DOCUMENT = 0
const val FAB_ID_CREATE_SECTION = 1