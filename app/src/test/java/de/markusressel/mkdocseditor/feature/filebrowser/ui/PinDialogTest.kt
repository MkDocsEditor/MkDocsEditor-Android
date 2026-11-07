package de.markusressel.mkdocseditor.feature.filebrowser.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.dialog.ItemDialogs
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class PinDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(item: WikiItem) = composeRule.setContent {
        MkDocsEditorTheme {
            ItemDialogs(
                dialogState = DialogState.Pin(item),
                onAction = { _, _ -> },
                onMovePreview = { _, _ -> },
                onMoveBack = {},
                onMoveApply = {},
                onBacklinkClicked = {},
                onDeleteConfirmed = {},
                onDismissRequest = {},
            )
        }
    }

    @Test
    fun `the pin dialog names a page by its title, with its file name`() {
        show(WikiItem.Document(documentData("d1", name = "setup").copy(title = "Getting started")))
        composeRule.onNodeWithText("Pin Getting started").assertExists()
        composeRule.onNodeWithText("setup.md").assertExists()
    }

    @Test
    fun `the pin dialog names a page without a title by its file name`() {
        show(WikiItem.Document(documentData("d1", name = "setup")))
        composeRule.onNodeWithText("Pin setup").assertExists()
        composeRule.onNodeWithText("setup.md").assertDoesNotExist()
    }
}
