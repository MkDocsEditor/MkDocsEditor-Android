package de.markusressel.mkdocseditor.feature.filebrowser.ui

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserScreenContent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class GestureTestScreen(private val content: @Composable () -> Unit) : Screen {
    @Composable
    override fun Content() = content()
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class FolderBackGestureTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<UiEvent>()

    private val root = SectionItem("root", "/")
    private val folder = SectionItem("a", "a")

    private fun show(state: UiState) {
        composeRule.setContent {
            MkDocsEditorTheme {
                // the top bar needs a navigator
                Navigator(GestureTestScreen { FileBrowserScreenContent(uiState = state, onUiEvent = { events += it }) })
            }
        }
    }

    private fun showSubfolder() = show(
        UiState(
            currentSectionPath = listOf(root, folder),
            folderContent = FolderContent(sectionId = "a", documents = listOf(documentData("in-a"))),
            parentFolderContent = FolderContent(sectionId = "root", documents = listOf(documentData("in-root"))),
        )
    )

    private val dispatcher get() = composeRule.activity.onBackPressedDispatcher

    private fun drag(progress: Float) {
        composeRule.runOnUiThread {
            dispatcher.dispatchOnBackStarted(BackEventCompat(0F, 500F, 0F, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(100F, 500F, progress, BackEventCompat.EDGE_LEFT))
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `dragging the back gesture shows the parent next to the open folder`() {
        showSubfolder()

        drag(0.5F)

        composeRule.onNodeWithText("in-root").assertIsDisplayed()
        composeRule.onNodeWithText("in-a").assertIsDisplayed()
        assertEquals(emptyList<UiEvent>(), events)
    }

    @Test
    fun `releasing the gesture goes up and keeps the parent while it opens`() {
        showSubfolder()
        drag(0.5F)

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<UiEvent>(UiEvent.NavigateUp), events)
        // the state still has the subfolder open, as long as the parent has not loaded
        composeRule.onNodeWithText("in-root").assertIsDisplayed()
        composeRule.onNodeWithText("in-a").assertDoesNotExist()
    }

    @Test
    fun `cancelling the gesture returns to the open folder`() {
        showSubfolder()
        drag(0.5F)

        composeRule.runOnUiThread { dispatcher.dispatchOnBackCancelled() }
        composeRule.waitForIdle()

        assertEquals(emptyList<UiEvent>(), events)
        composeRule.onNodeWithText("in-a").assertIsDisplayed()
        composeRule.onNodeWithText("in-root").assertDoesNotExist()
    }

    @Test
    fun `without a stored parent, releasing still goes up`() {
        show(
            UiState(
                currentSectionPath = listOf(root, folder),
                folderContent = FolderContent(sectionId = "a", documents = listOf(documentData("in-a"))),
            )
        )
        drag(0.5F)

        composeRule.runOnUiThread { dispatcher.onBackPressed() }
        composeRule.waitForIdle()

        assertEquals(listOf<UiEvent>(UiEvent.NavigateUp), events)
    }

    @Test
    fun `the top-level folder leaves back to the system`() {
        show(UiState(currentSectionPath = listOf(root), folderContent = FolderContent(sectionId = "root")))

        assertFalse(dispatcher.hasEnabledCallbacks())
    }
}
