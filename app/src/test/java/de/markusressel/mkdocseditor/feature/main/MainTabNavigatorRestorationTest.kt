package de.markusressel.mkdocseditor.feature.main

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.CurrentScreen
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabNavigator
import cafe.adriel.voyager.navigator.tab.TabOptions
import de.markusressel.mkdocseditor.feature.main.ui.compose.MainTabNavigator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A rotation recreates the activity, but not the view model of the main screen. The user has to stay on the tab
 * and with the screens they had open.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class MainTabNavigatorRestorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val restoration = StateRestorationTester(composeRule)

    /** like the view model's requests: they outlive the recreation */
    private val requests = Channel<Unit>(Channel.BUFFERED)

    private lateinit var tabNavigator: TabNavigator

    @Test
    fun `a request for the file browser is not shown again after a recreation`() {
        setTabs(requests.receiveAsFlow())

        requests.trySend(Unit)
        composeRule.onNodeWithText(FILES_ROOT).assertIsDisplayed()

        composeRule.runOnIdle { tabNavigator.current = DashboardTestTab }
        composeRule.onNodeWithText(DASHBOARD).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithText(DASHBOARD).assertIsDisplayed()
    }

    @Test
    fun `a document open in the file browser stays open after a recreation`() {
        setTabs(requests.receiveAsFlow())
        composeRule.runOnIdle { tabNavigator.current = FilesTestTab }
        composeRule.onNodeWithText(FILES_ROOT).assertIsDisplayed()
        composeRule.runOnIdle { documentNavigator.push(DocumentScreen) }
        composeRule.onNodeWithText(DOCUMENT).assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithText(DOCUMENT).assertIsDisplayed()

        // and after going to another tab and back
        composeRule.runOnIdle { tabNavigator.current = DashboardTestTab }
        composeRule.onNodeWithText(DASHBOARD).assertIsDisplayed()
        composeRule.runOnIdle { tabNavigator.current = FilesTestTab }
        composeRule.onNodeWithText(DOCUMENT).assertIsDisplayed()
    }

    private fun setTabs(fileBrowserRequests: Flow<Unit>) {
        restoration.setContent {
            MainTabNavigator(
                initialTab = StartTestTab,
                fileBrowserRequests = fileBrowserRequests,
                fileBrowserTab = FilesTestTab,
            ) { navigator ->
                tabNavigator = navigator
                CurrentTab()
            }
        }
    }
}

private const val START = "start"
private const val DASHBOARD = "dashboard"
private const val FILES_ROOT = "files root"
private const val DOCUMENT = "document"

/** the navigator of the file browser tab, to open a document in it */
private lateinit var documentNavigator: Navigator

private object StartTestTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 0u, title = START)

    @Composable
    override fun Content() = Text(START)
}

private object DashboardTestTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 1u, title = DASHBOARD)

    @Composable
    override fun Content() = Text(DASHBOARD)
}

private object FilesTestTab : Tab {
    override val options: TabOptions @Composable get() = TabOptions(index = 2u, title = FILES_ROOT)

    @Composable
    override fun Content() {
        Navigator(FilesRootScreen) { navigator ->
            documentNavigator = navigator
            CurrentScreen()
        }
    }
}

private object FilesRootScreen : Screen {
    @Composable
    override fun Content() = Text(FILES_ROOT)
}

private object DocumentScreen : Screen {
    @Composable
    override fun Content() = Text(DOCUMENT)
}
