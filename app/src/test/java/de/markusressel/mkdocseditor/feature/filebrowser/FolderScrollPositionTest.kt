package de.markusressel.mkdocseditor.feature.filebrowser

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.ui.UiState
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserScreenContent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.opensDeeper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private class BrowserScreen(private val content: @Composable () -> Unit) : Screen {
    @Composable
    override fun Content() = content()
}

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class FolderScrollPositionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun folder(id: String) = FolderContent(
        sectionId = id,
        documents = List(60) { documentData("$id-$it") },
    )

    private var content by mutableStateOf<FolderContent?>(folder("parent"))

    private fun showBrowser() {
        composeRule.setContent {
            MkDocsEditorTheme {
                // the top bar needs a navigator
                Navigator(BrowserScreen { FileBrowserScreenContent(uiState = UiState(folderContent = content), onUiEvent = {}) })
            }
        }
    }

    private fun scrollList(index: Int) {
        composeRule.onNode(hasScrollToIndexAction()).performScrollToIndex(index)
        composeRule.waitForIdle()
    }

    private fun show(folder: FolderContent?) {
        content = folder
        composeRule.waitForIdle()
    }

    @Test
    fun `a folder opens at its top`() {
        showBrowser()
        scrollList(40)

        show(folder("child"))

        composeRule.onNodeWithText("child-0").assertIsDisplayed()
    }

    @Test
    fun `a folder is where it was after coming back from a subfolder`() {
        showBrowser()
        scrollList(40)
        composeRule.onNodeWithText("parent-39").assertIsDisplayed()

        show(folder("child"))
        scrollList(20)
        show(folder("parent"))

        composeRule.onNodeWithText("parent-39").assertIsDisplayed()
    }

    @Test
    fun `a folder keeps its position while the content is unknown for a moment`() {
        showBrowser()
        scrollList(40)

        show(null)
        show(folder("parent"))

        composeRule.onNodeWithText("parent-39").assertIsDisplayed()
    }

    @Test
    fun `new content of the same folder shows`() {
        showBrowser()

        show(FolderContent(sectionId = "parent", documents = listOf(documentData("added"))))

        composeRule.onNodeWithText("added").assertIsDisplayed()
    }

    private val path = listOf(SectionItem("root", "/"), SectionItem("a", "a"))

    @Test
    fun `opening a subfolder goes deeper`() = assertTrue(opensDeeper(from = "root", to = "a", path = path))

    @Test
    fun `going up does not go deeper`() = assertFalse(opensDeeper(from = "b", to = "a", path = path))
}
