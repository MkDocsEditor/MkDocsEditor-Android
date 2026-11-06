package de.markusressel.mkdocseditor.feature.theme.domain

import android.app.Application
import android.app.UiModeManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/** The setting values are resources, so this needs Robolectric */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class ApplyThemeUseCaseTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val modes = mutableListOf<Int>()
    private val applyTheme = ApplyThemeUseCase(context, ApplicationNightMode { modes += it })

    @Test
    fun `the light theme turns the night mode of the app off`() {
        applyTheme(context.getString(R.string.theme_light_value))

        assertEquals(listOf(UiModeManager.MODE_NIGHT_NO), modes)
    }

    @Test
    fun `the dark theme turns the night mode of the app on`() {
        applyTheme(context.getString(R.string.theme_dark_value))

        assertEquals(listOf(UiModeManager.MODE_NIGHT_YES), modes)
    }

    @Test
    fun `the system theme lets the app follow the system`() {
        applyTheme(context.getString(R.string.theme_system_value))

        assertEquals(listOf(UiModeManager.MODE_NIGHT_AUTO), modes)
    }
}
