package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.feature.editor.data.ScrollSyncSetting
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Turns the split scroll sync on or off, from its stored value, so that the menu and the settings never disagree */
internal class ToggleScrollSyncUseCase @Inject constructor(
    private val setting: ScrollSyncSetting,
) {
    /** @return whether the sync is on now */
    suspend operator fun invoke(): Boolean {
        val enabled = !setting.enabled.first()
        setting.set(enabled)
        return enabled
    }
}
