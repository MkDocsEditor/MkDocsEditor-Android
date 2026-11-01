package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.Navigator
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.editor.ui.EditorField
import de.markusressel.mkdocseditor.feature.editor.ui.compose.CodeEditorScreenContent
import de.markusressel.mkdocseditor.feature.editor.ui.compose.LOADING_INDICATOR_TAG
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
                editorField = EditorField(),
                onTextChanged = {},
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
        composeRule.onNodeWithText("Not available offline", substring = true, ignoreCase = true).assertDoesNotExist()
        composeRule.onNodeWithText("no offline version available", substring = true).assertIsDisplayed()
    }

    @Test
    fun `no notice without a reason`() {
        show(CodeEditorViewModel.UiState())
        composeRule.onNodeWithText("deleted on the server", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("no offline version available", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Offline").assertDoesNotExist()
    }
}
