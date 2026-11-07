package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import androidx.lifecycle.SavedStateHandle

/**
 * The change a page was opened at, from the arguments of its screen. The page goes there once: the screen
 * hands its arguments again after a rotation, and Android restores them after process death, and neither may move the
 * page again. So whether they were taken is in the saved state.
 */
internal class ChangeJumpArguments(private val handle: SavedStateHandle) {

    /** @return the change to go to, null if the arguments were taken before or name none */
    fun take(change: ChangeLocation?): ChangeLocation? {
        if (handle.get<Boolean>(KEY_TAKEN) == true) return null
        handle[KEY_TAKEN] = true
        return change
    }

    private companion object {
        const val KEY_TAKEN = "editor_change_jump_taken"
    }
}
