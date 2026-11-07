package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreenContent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A rotation recreates the screen, but not the view model, which holds the editor's state. The editor has to come back
 * the way the user left it.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class EditorRecreationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val restoration = StateRestorationTester(composeRule)

    private val editorState = recreatedEditorState

    private val editor = SemanticsMatcher.keyIsDefined(SemanticsActions.SetText)

    private fun show(uiState: CodeEditorViewModel.UiState) {
        recreatedUiState = uiState
        restoration.setContent {
            MkDocsEditorTheme {
                Navigator(RecreatedEditorScreen)
            }
        }
    }

    @Test
    fun `the editor has the focus and the selection again after a recreation in the edit mode`() {
        show(CodeEditorViewModel.UiState(editModeActive = true, splitMode = SplitMode.Off))
        composeRule.onNode(editor).performSemanticsAction(SemanticsActions.RequestFocus)
        composeRule.runOnIdle { editorState.setSelection(TextRange(9, 13)) }
        composeRule.onNode(editor).assertIsFocused()

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNode(editor).assertIsFocused()
        composeRule.runOnIdle {
            assertEquals(TextRange(9, 13), editorState.selection)
            assertEquals("# Title\n\nSome text\n", editorState.text)
        }
    }

    @Test
    fun `an editor without the focus stays without it after a recreation`() {
        show(CodeEditorViewModel.UiState(editModeActive = true, splitMode = SplitMode.Off))

        restoration.emulateSavedInstanceStateRestore()

        composeRule.onNode(editor).assertIsNotFocused()
    }
}

/** like the view model's: it outlives the recreation */
private val recreatedEditorState get() = KodeEditorState("# Title\n\nSome text\n").also { currentEditorState = it }

private var currentEditorState: KodeEditorState = KodeEditorState()
private var recreatedUiState = CodeEditorViewModel.UiState()

/** the top bar needs a navigator, and the navigator saves its screens, so this has no fields */
private object RecreatedEditorScreen : Screen {
    @Composable
    override fun Content() {
        CodeEditorScreenContent(uiState = recreatedUiState, editorState = currentEditorState, onUiEvent = {})
    }
}
