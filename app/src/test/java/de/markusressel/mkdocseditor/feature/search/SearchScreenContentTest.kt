package de.markusressel.mkdocseditor.feature.search

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import de.markusressel.mkdocseditor.feature.search.ui.compose.SearchScreenContent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36], qualifiers = "w411dp-h891dp")
class SearchScreenContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<SearchViewModel.UiEvent>()

    private fun show(state: SearchViewModel.UiState) = composeRule.setContent {
        MkDocsEditorTheme {
            SearchScreenContent(uiState = state, onUiEvent = { events += it })
        }
    }

    /**
     * Regression: with the deprecated SearchBar overload the input field filled the whole
     * screen, so the results were never visible
     */
    @Test
    fun `the results are shown below a normal sized input field`() {
        show(
            SearchViewModel.UiState(
                currentSearchFilter = "setup",
                currentSearchResults = listOf(
                    SearchResultItem.Document("d-setup", "setup", emptyList()),
                    SearchResultItem.Section("s-guide", "guide"),
                ),
            )
        )
        val input = composeRule.onNode(hasSetTextAction()).getBoundsInRoot()
        assertTrue(input.bottom - input.top < 120.dp, "input field is ${input.bottom - input.top} high")
        composeRule.onNodeWithText("Results: 2").assertIsDisplayed()
        composeRule.onNodeWithText("guide").performClick()
        assertEquals(SearchViewModel.UiEvent.SearchResultClicked(SearchResultItem.Section("s-guide", "guide")), events.last())
    }

    @Test
    fun `typing changes the search`() {
        show(SearchViewModel.UiState())
        composeRule.onNode(hasSetTextAction()).performTextInput("Kü")
        assertEquals(SearchViewModel.UiEvent.SearchInputChanged("Kü"), events.last())
    }
}
