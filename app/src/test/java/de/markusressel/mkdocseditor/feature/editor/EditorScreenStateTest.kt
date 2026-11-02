package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionIndicator
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CONNECTION_ACTION_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreenContent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.LOADING_INDICATOR_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SPLIT_DIVIDER_TAG
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class EditorScreenStateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class EditorScreen(private val uiState: CodeEditorViewModel.UiState) : Screen {
        @Composable
        override fun Content() {
            CodeEditorScreenContent(
                uiState = uiState,
                editorState = KodeEditorState(),
                onUiEvent = {},
            )
        }
    }

    private fun show(uiState: CodeEditorViewModel.UiState) = composeRule.setContent {
        MkDocsEditorTheme {
            Navigator(EditorScreen(uiState))
        }
    }

    @Test
    fun `a loading document shows the indicator`() {
        show(CodeEditorViewModel.UiState(loading = true))
        composeRule.onNodeWithTag(LOADING_INDICATOR_TAG).assertIsDisplayed()
    }

    @Test
    fun `a loaded document shows no indicator`() {
        show(CodeEditorViewModel.UiState(loading = false))
        composeRule.onNodeWithTag(LOADING_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `a page deleted on the server says so`() {
        show(CodeEditorViewModel.UiState(isDocumentDeletedOnServer = true))
        composeRule.onNodeWithText("This page was deleted on the server. Your copy stays on this device.").assertIsDisplayed()
    }

    @Test
    fun `a page without an offline version says so`() {
        show(CodeEditorViewModel.UiState(isNoOfflineVersionVisible = true))
        composeRule.onNodeWithText("not on this device yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun `no notice without a reason`() {
        show(CodeEditorViewModel.UiState())
        composeRule.onNodeWithText("deleted on the server", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("not on this device yet", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag(CONNECTION_ACTION_TAG).assertDoesNotExist()
    }

    @Test
    fun `the connection is a small icon, not a message`() {
        show(CodeEditorViewModel.UiState(connectionIndicator = ConnectionIndicator.NotConnected))
        composeRule.onNodeWithContentDescription("Not connected").assertIsDisplayed()
    }

    @Test
    fun `unsynced changes have their own icon`() {
        show(CodeEditorViewModel.UiState(connectionIndicator = ConnectionIndicator.UnsyncedChanges))
        composeRule.onNodeWithContentDescription("Changes not synced yet").assertIsDisplayed()
    }

    private val editor = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    @Test
    fun `a document opens on the rendered page`() {
        show(CodeEditorViewModel.UiState())
        composeRule.onNode(editor).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Rendered page hidden, tap to show it below the editor").assertDoesNotExist()
    }

    @Test
    fun `the edit mode of a phone shows only the code editor`() {
        show(CodeEditorViewModel.UiState(editModeActive = true))
        composeRule.onNode(editor).assertIsDisplayed()
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Rendered page hidden, tap to show it below the editor").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `the edit mode of a wide screen splits on its own`() {
        show(CodeEditorViewModel.UiState(editModeActive = true))
        composeRule.onNode(editor).assertIsDisplayed()
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Rendered page next to the editor, tap to hide it").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun `the choice of the user beats the window`() {
        show(CodeEditorViewModel.UiState(editModeActive = true, splitMode = SplitMode.Off))
        composeRule.onNodeWithTag(SPLIT_DIVIDER_TAG).assertDoesNotExist()
    }

    /** the keyboard takes away height: a wide box must not build a new editor, which closes the keyboard again */
    @Test
    fun `the editor keeps the focus when the keyboard makes the screen wider than high`() {
        var height by mutableStateOf(800.dp)
        composeRule.setContent {
            MkDocsEditorTheme {
                Box(Modifier.height(height)) {
                    Navigator(EditorScreen(CodeEditorViewModel.UiState(editModeActive = true)))
                }
            }
        }
        val editor = composeRule.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.SetText))
        editor.performSemanticsAction(SemanticsActions.RequestFocus)
        editor.assertIsFocused()

        height = 300.dp
        composeRule.waitForIdle()

        editor.assertIsFocused()
    }
}
