package de.markusressel.mkdocseditor.feature.filebrowser

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.FileBrowserList
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class DocumentListEntryTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `only a page with unsynced changes says so`() {
        composeRule.setContent {
            MkDocsEditorTheme {
                FileBrowserList(
                    items = listOf(documentData("one"), documentData("two")),
                    unsyncedDocumentIds = setOf("two"),
                    onDocumentClicked = {},
                    onDocumentLongClicked = {},
                    onResourceClicked = {},
                    onResourceLongClicked = {},
                    onSectionClicked = {},
                    onSectionLongClicked = {},
                )
            }
        }
        composeRule.onNodeWithText("Changes not synced yet").assertIsDisplayed()
    }
}
