package de.markusressel.mkdocseditor.feature.editor

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class EditorLoadingIndicatorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun show(loading: Boolean) = composeRule.setContent {
        MkDocsEditorTheme {
            CodeEditorScreenContent(
                uiState = CodeEditorViewModel.UiState(loading = loading),
                editorField = EditorField(),
                onTextChanged = {},
                onUiEvent = {},
            )
        }
    }

    @Test
    fun `a loading document shows the indicator`() {
        show(loading = true)
        composeRule.onNodeWithTag(LOADING_INDICATOR_TAG).assertIsDisplayed()
    }

    @Test
    fun `a loaded document shows no indicator`() {
        show(loading = false)
        composeRule.onNodeWithTag(LOADING_INDICATOR_TAG).assertDoesNotExist()
    }
}
