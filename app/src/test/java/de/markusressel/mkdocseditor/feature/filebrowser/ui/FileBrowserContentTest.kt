package de.markusressel.mkdocseditor.feature.filebrowser.ui

import android.app.Application
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserList
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.CreateItemForm
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class FileBrowserContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun section(name: String, subsections: List<SectionData> = emptyList()) =
        SectionData(0, "s-$name", name, subsections, emptyList(), emptyList())

    private fun showList(content: FolderContent?, isLoading: Boolean = false) {
        composeRule.setContent {
            MkDocsEditorTheme {
                FileBrowserList(
                    content = content,
                    isLoading = isLoading,
                    onDocumentClicked = {},
                    onDocumentLongClicked = {},
                    onResourceClicked = {},
                    onResourceLongClicked = {},
                    onSectionClicked = {},
                    onSectionLongClicked = {},
                )
            }
        }
    }

    @Test
    fun `a folder that is still loading is not called empty`() {
        showList(content = null, isLoading = true)
        composeRule.onNodeWithText("No files").assertDoesNotExist()
    }

    @Test
    fun `a folder without anything says so`() {
        showList(content = FolderContent())
        composeRule.onNodeWithText("No files").assertExists()
    }

    @Test
    fun `an entry of a folder only names what is in it`() {
        showList(content = FolderContent(sections = listOf(section("full", listOf(section("child"))))))
        composeRule.onNodeWithText("Subsections: 1").assertExists()
        composeRule.onNodeWithText("Documents: 0").assertDoesNotExist()
    }

    private val created = mutableListOf<String>()

    private fun showDialog(folder: FolderContent = FolderContent(sections = listOf(section("guide")))) {
        composeRule.setContent {
            MkDocsEditorTheme {
                CreateItemForm(
                    kind = NewItemKind.Section,
                    folder = folder,
                    onDismissRequest = {},
                    onCreateClicked = { created += it },
                )
            }
        }
    }

    private fun type(text: String) {
        composeRule.onNodeWithText("New Section").performTextInput(text)
    }

    @Test
    fun `an empty name cannot be created and is not an error yet`() {
        showDialog()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
        composeRule.onNodeWithText("This name already exists in this folder").assertDoesNotExist()
    }

    @Test
    fun `a name that is taken says so and keeps the dialog open`() {
        showDialog()
        type("guide")

        composeRule.onNodeWithText("This name already exists in this folder").assertExists()
        composeRule.onNodeWithText("Create").assertIsNotEnabled()
    }

    @Test
    fun `a good name is handed on`() {
        showDialog()
        type("new")
        composeRule.onNodeWithText("Create").assertIsEnabled().performClick()

        assertEquals(listOf("new"), created)
    }
}
