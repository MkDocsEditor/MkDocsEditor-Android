package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorBottomBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class CodeEditorBottomBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<UiEvent>()

    private fun setContent() = composeRule.setContent {
        MkDocsEditorTheme {
            CodeEditorBottomBar(uiState = CodeEditorViewModel.UiState(), onUiEvent = { events += it })
        }
    }

    private fun click(description: String) =
        composeRule.onNodeWithContentDescription(description).performScrollTo().performClick()

    @Test
    fun `reading shows the toggle into the edit mode, but no formatting`() {
        composeRule.setContent {
            MkDocsEditorTheme {
                CodeEditorBottomBar(
                    uiState = CodeEditorViewModel.UiState(
                        editModeActive = false,
                        fabConfig = FabConfig(right = CodeEditorViewModel.editModeFabs(editable = true, editModeActive = false)),
                    ),
                    onUiEvent = { events += it },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Bold").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("gmi_edit").performClick()
        assertEquals(
            listOf<UiEvent>(UiEvent.ExpandableFabItemSelected(CodeEditorViewModel.EnableEditModeFabConfig)),
            events,
        )
    }

    @Test
    fun `the edit mode toggle follows the state`() {
        assertEquals(
            listOf(CodeEditorViewModel.EnableEditModeFabConfig),
            CodeEditorViewModel.editModeFabs(editable = true, editModeActive = false),
        )
        assertEquals(
            listOf(CodeEditorViewModel.DisableEditModeFabConfig),
            CodeEditorViewModel.editModeFabs(editable = true, editModeActive = true),
        )
        assertEquals(emptyList(), CodeEditorViewModel.editModeFabs(editable = false, editModeActive = false))
    }

    @Test
    fun `every toolbar action sends its event`() {
        setContent()
        val expected = mapOf(
            "Bold" to UiEvent.FormatClicked(FormatAction.Bold),
            "Italic" to UiEvent.FormatClicked(FormatAction.Italic),
            "Strikethrough" to UiEvent.FormatClicked(FormatAction.Strikethrough),
            "Inline code" to UiEvent.FormatClicked(FormatAction.InlineCode),
            "Code block" to UiEvent.FormatClicked(FormatAction.CodeBlock),
            "Bulleted list" to UiEvent.FormatClicked(FormatAction.BulletList),
            "Numbered list" to UiEvent.FormatClicked(FormatAction.NumberedList),
            "Task list" to UiEvent.FormatClicked(FormatAction.TaskList),
            "Quote" to UiEvent.FormatClicked(FormatAction.Quote),
            "Link" to UiEvent.InsertLinkClicked,
            "Image" to UiEvent.InsertImageClicked,
            "Table" to UiEvent.FormatClicked(FormatAction.Table),
            "Horizontal rule" to UiEvent.FormatClicked(FormatAction.HorizontalRule),
        )
        expected.forEach { (description, event) ->
            events.clear()
            click(description)
            assertEquals(listOf<UiEvent>(event), events, description)
        }
    }

    @Test
    fun `headings and admonitions are picked from a menu`() {
        setContent()
        click("Heading")
        composeRule.onNodeWithText("Heading 3").performClick()
        click("Admonition")
        composeRule.onNodeWithText("Warning").performClick()
        assertEquals(
            listOf<UiEvent>(
                UiEvent.FormatClicked(FormatAction.Heading(3)),
                UiEvent.FormatClicked(FormatAction.Admonition("warning")),
            ),
            events,
        )
    }
}
