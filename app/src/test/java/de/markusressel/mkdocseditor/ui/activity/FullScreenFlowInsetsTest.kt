package de.markusressel.mkdocseditor.ui.activity

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * The screens of a link cover the whole app, so no bottom bar of the main screen makes room for the navigation bar
 * there. They have to do it themselves, without breaking the keyboard padding of their text fields.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w400dp-h800dp-mdpi")
class FullScreenFlowInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    @Test
    fun `the content of a full screen flow ends above the navigation bar`() {
        setFlow()
        applyInsets(navigationBarBottom = 48, imeBottom = 0)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertEquals(root.bottom - 48.dp, content.bottom)
    }

    @Test
    fun `with the keyboard open, the content ends at the keyboard and not above it`() {
        setFlow()
        applyInsets(navigationBarBottom = 48, imeBottom = 300)

        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        val content = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertEquals(root.bottom - 300.dp, content.bottom)
    }

    private fun setFlow() {
        composeRule.setContent {
            view = LocalView.current
            MkDocsEditorTheme {
                FullScreenFlowSurface {
                    // as the sign-in screens lay out their text fields
                    TabContentScaffold { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues)
                                .imePadding()
                                .testTag(CONTENT_TAG)
                        ) {
                            Text("Sign in")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun applyInsets(navigationBarBottom: Int, imeBottom: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navigationBarBottom))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imeBottom))
            .setVisible(WindowInsetsCompat.Type.ime(), imeBottom > 0)
            .build()
        composeRule.runOnIdle { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CONTENT_TAG = "content"
    }
}
