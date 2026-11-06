package de.markusressel.mkdocseditor.feature.theme.domain

import android.app.UiModeManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the theme setting to the whole app. It sets the night mode of the app instead of passing the theme down, so
 * everything that asks `isSystemInDarkTheme()`, like the editor and the file icons, follows it too.
 */
@Singleton
internal class ApplyThemeUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nightMode: ApplicationNightMode,
) {

    /**
     * @param theme the persisted value of the theme setting
     */
    operator fun invoke(theme: String) {
        nightMode.set(
            when (theme) {
                context.getString(R.string.theme_light_value) -> UiModeManager.MODE_NIGHT_NO
                context.getString(R.string.theme_dark_value) -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            }
        )
    }
}

/**
 * The night mode of this app, `UiModeManager.setApplicationNightMode`. Android keeps it across restarts.
 */
internal fun interface ApplicationNightMode {
    fun set(mode: Int)
}
