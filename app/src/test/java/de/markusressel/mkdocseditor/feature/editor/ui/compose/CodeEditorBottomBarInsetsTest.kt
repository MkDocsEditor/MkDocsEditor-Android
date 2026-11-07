package de.markusressel.mkdocseditor.feature.editor.ui.compose

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The formatting toolbar sits at the bottom of the screen, so the status bar at the top must not make it taller. In
 * landscape it still keeps its buttons clear of the navigation bar and the cutout at the sides.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class CodeEditorBottomBarInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    @Test
    @Config(qualifiers = "w400dp-h800dp-port-mdpi")
    fun `the toolbar keeps its height in portrait with a status bar at the top`() {
        setScreen()
        applyInsets(cutout = Insets.NONE, navigationBars = Insets.of(0, 0, 0, 48))

        assertEquals(BOTTOM_APP_BAR_HEIGHT, composeRule.onNodeWithTag(BAR_TAG).getUnclippedBoundsInRoot().height)
    }

    @Test
    @Config(qualifiers = "w800dp-h400dp-land-mdpi")
    fun `the toolbar keeps its height in landscape and stays clear of a cutout at the side`() {
        setScreen()
        applyInsets(cutout = Insets.of(40, 0, 0, 0), navigationBars = Insets.of(0, 0, 48, 0))

        val bar = composeRule.onNodeWithTag(BAR_TAG).getUnclippedBoundsInRoot()
        assertEquals(BOTTOM_APP_BAR_HEIGHT, bar.height)
        val bold = composeRule.onNodeWithContentDescription("Bold").getUnclippedBoundsInRoot()
        assertTrue(bold.left >= 40.dp, "the first button is under the cutout: ${bold.left}")
    }

    private fun setScreen() {
        composeRule.setContent {
            view = LocalView.current
            MkDocsEditorTheme {
                TabContentScaffold(
                    bottomBar = {
                        CodeEditorBottomBar(
                            modifier = Modifier.fillMaxWidth().testTag(BAR_TAG),
                            uiState = CodeEditorViewModel.UiState(editModeActive = true),
                            onUiEvent = {},
                        )
                    },
                ) { paddingValues ->
                    Box(modifier = Modifier.fillMaxSize().padding(paddingValues))
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun applyInsets(cutout: Insets, navigationBars: Insets) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), navigationBars)
            .setInsets(WindowInsetsCompat.Type.displayCutout(), cutout)
            .build()
        composeRule.runOnIdle { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        composeRule.waitForIdle()
    }

    private companion object {
        const val BAR_TAG = "bottom-bar"

        // the container height of a Material 3 BottomAppBar, which keeps its tokens internal
        val BOTTOM_APP_BAR_HEIGHT = 80.dp
    }
}
