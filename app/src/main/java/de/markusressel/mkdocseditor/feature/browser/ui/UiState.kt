package de.markusressel.mkdocseditor.feature.browser.ui

import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig

internal data class UiState(
    val fabConfig: FabConfig = FabConfig(
        right = listOf(
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

    val currentSectionPath: String = "/",

    val listItems: List<IdentifiableListItem> = emptyList()
)

const val FAB_ID_CREATE_DOCUMENT = 0
const val FAB_ID_CREATE_SECTION = 1