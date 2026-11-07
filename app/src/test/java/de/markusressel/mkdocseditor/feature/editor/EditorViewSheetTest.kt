package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.AuthorHighlightMode
import de.markusressel.mkdocseditor.feature.editor.ui.compose.EditorSettings
import de.markusressel.mkdocseditor.feature.editor.ui.compose.EditorViewSheetContent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.VIEW_SHEET_ACCOUNT_COLOR_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.VIEW_SHEET_LINE_NUMBERS_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.VIEW_SHEET_LINE_WRAPPING_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.VIEW_SHEET_SCROLL_SYNC_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.VIEW_SHEET_TEXT_SIZE_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.cursorColorSwatches
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class EditorViewSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<UiEvent>()

    private fun show(
        settings: EditorSettings = EditorSettings(),
        localColor: String? = null,
        splitOpen: Boolean = true,
    ) {
        composeRule.setContent {
            MkDocsEditorTheme {
                EditorViewSheetContent(
                    settings = settings,
                    localColor = localColor,
                    splitOpen = splitOpen,
                    onUiEvent = { events += it },
                )
            }
        }
    }

    @Test
    fun `the sheet shows each setting with its stored value`() {
        show(settings = EditorSettings(authorHighlight = AuthorHighlightMode.Strip, textSize = 18))

        composeRule.onNodeWithText("Strip").assertIsSelected()
        composeRule.onNodeWithTag(VIEW_SHEET_SCROLL_SYNC_TAG).assertIsOn()
        composeRule.onNodeWithTag(VIEW_SHEET_LINE_WRAPPING_TAG).assertIsOn()
        composeRule.onNodeWithTag(VIEW_SHEET_LINE_NUMBERS_TAG).assertIsOn()
        composeRule.onNodeWithTag(VIEW_SHEET_TEXT_SIZE_TAG).assertRangeInfoEquals(ProgressBarRangeInfo(18f, 10f..24f, steps = 13))
        composeRule.onNodeWithTag(VIEW_SHEET_ACCOUNT_COLOR_TAG).assertIsSelected()
        composeRule.onNodeWithText("18 sp").assertExists()
    }

    @Test
    fun `the scroll sync is only there while a split is open`() {
        show(splitOpen = false)

        composeRule.onNodeWithTag(VIEW_SHEET_SCROLL_SYNC_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(VIEW_SHEET_LINE_WRAPPING_TAG).assertExists()
    }

    @Test
    fun `each control sends the new value, the sheet itself keeps nothing`() {
        show(settings = EditorSettings(authorHighlight = AuthorHighlightMode.StripAndMarker), localColor = cursorColorSwatches[2])

        composeRule.onNodeWithText("Off").performClick()
        composeRule.onNodeWithTag(VIEW_SHEET_SCROLL_SYNC_TAG).performClick()
        composeRule.onNodeWithTag(VIEW_SHEET_LINE_WRAPPING_TAG).performClick()
        composeRule.onNodeWithTag(VIEW_SHEET_LINE_NUMBERS_TAG).performClick()
        composeRule.onNodeWithTag(VIEW_SHEET_TEXT_SIZE_TAG).performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        composeRule.onNodeWithContentDescription(cursorColorSwatches[8]).performClick()
        composeRule.onNodeWithTag(VIEW_SHEET_ACCOUNT_COLOR_TAG).performClick()

        assertEquals(
            listOf(
                UiEvent.AuthorHighlightChosen(AuthorHighlightMode.Off),
                UiEvent.ScrollSyncChanged(false),
                UiEvent.LineWrappingChanged(false),
                UiEvent.LineNumbersChanged(false),
                UiEvent.TextSizeChanged(20),
                UiEvent.CursorColorChosen(cursorColorSwatches[8]),
                UiEvent.CursorColorChosen(null),
            ),
            events,
        )
    }
}
