package de.markusressel.mkdocseditor.feature.common.ui.compose

import android.app.Application
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.main.ui.compose.ContentBesideRail
import de.markusressel.mkdocseditor.feature.main.ui.compose.MkDocsEditorNavigationRail
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * In landscape the navigation bar and the display cutout sit at the sides of the screen. The screens must keep their
 * controls clear of both, and must not make room for them twice.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w800dp-h400dp-land-mdpi")
class LandscapeInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var view: View

    @Test
    fun `the content and the top bar actions stay clear of a cutout and a navigation bar at the sides`() {
        setScreen { TabScreen() }
        applyInsets(cutoutLeft = 40, navigationBarRight = 48)

        val content = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertEquals(40.dp, content.left)
        assertEquals(800.dp - 48.dp, content.right)

        val search = composeRule.onNodeWithContentDescription("gmi_search").getUnclippedBoundsInRoot()
        assertTrue(search.right <= 800.dp - 48.dp, "the search action is under the navigation bar: ${search.right}")
        val title = composeRule.onNodeWithText(TITLE).getUnclippedBoundsInRoot()
        assertTrue(title.left >= 40.dp, "the title is under the cutout: ${title.left}")
    }

    @Test
    fun `the rail makes room for a cutout at the start, and the content next to it does not do that again`() {
        setScreen {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.testTag(RAIL_TAG)) {
                    MkDocsEditorNavigationRail(
                        navItems = listOf(NavItem.Dashboard, NavItem.FileBrowser),
                        selectedNavItem = NavItem.Dashboard,
                        onItemSelected = {},
                    )
                }
                ContentBesideRail(railShown = true) {
                    TabScreen()
                }
            }
        }
        applyInsets(cutoutLeft = 40, navigationBarRight = 48)

        val rail = composeRule.onNodeWithTag(RAIL_TAG).getUnclippedBoundsInRoot()
        val firstItem = composeRule.onNodeWithText("Dashboard").getUnclippedBoundsInRoot()
        assertTrue(firstItem.left >= 40.dp, "the rail item is under the cutout: ${firstItem.left}")

        val content = composeRule.onNodeWithTag(CONTENT_TAG).getUnclippedBoundsInRoot()
        assertEquals(rail.right, content.left)
        assertEquals(800.dp - 48.dp, content.right)
    }

    @Composable
    private fun TabScreen() {
        TabContentScaffold(
            topBar = {
                MkDocsEditorTopAppBar(
                    title = TITLE,
                    canGoBack = false,
                    actions = listOf(TopAppBarAction.FileBrowser.Search),
                )
            },
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .testTag(CONTENT_TAG)
            )
        }
    }

    private fun setScreen(content: @Composable () -> Unit) {
        composeRule.setContent {
            view = LocalView.current
            MkDocsEditorTheme {
                // the top bar goes back with the navigator
                Navigator(TestScreen(content))
            }
        }
        composeRule.waitForIdle()
    }

    private fun applyInsets(cutoutLeft: Int, navigationBarRight: Int) {
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, 24, 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, navigationBarRight, 0))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(cutoutLeft, 0, 0, 0))
            .build()
        composeRule.runOnIdle { ViewCompat.dispatchApplyWindowInsets(view, insets) }
        composeRule.waitForIdle()
    }

    private class TestScreen(private val body: @Composable () -> Unit) : Screen {
        @Composable
        override fun Content() = body()
    }

    private companion object {
        const val TITLE = "Title"
        const val CONTENT_TAG = "content"
        const val RAIL_TAG = "rail"
    }
}
