package de.markusressel.mkdocseditor.feature.editor.ui

import androidx.lifecycle.SavedStateHandle
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode

/**
 * The mode of the editor in the saved state of its screen. Android can kill the app in the background and restore the
 * screen later, and the page then comes back the way the user left it: editing or reading, with the same split.
 */
internal class SavedEditorMode(private val handle: SavedStateHandle) {

    /** whether the edit mode was on, or about to open */
    var editing: Boolean
        get() = handle[KEY_EDITING] ?: false
        set(value) {
            handle[KEY_EDITING] = value
        }

    /** what the user chose for the split, null for what fits the window */
    var splitMode: SplitMode?
        get() = handle.get<String>(KEY_SPLIT_MODE)?.let { name -> SplitMode.entries.firstOrNull { it.name == name } }
        set(value) {
            handle[KEY_SPLIT_MODE] = value?.name
        }

    /** keeps what [state] shows */
    fun save(state: CodeEditorViewModel.UiState) {
        editing = state.editModeActive || state.editModeOpening
        splitMode = state.splitMode
    }

    private companion object {
        const val KEY_EDITING = "editor_editing"
        const val KEY_SPLIT_MODE = "editor_split_mode"
    }
}
