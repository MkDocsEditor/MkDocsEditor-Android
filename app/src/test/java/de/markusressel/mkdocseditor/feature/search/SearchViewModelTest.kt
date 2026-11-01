package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.search.data.RecentSearchStore
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val eventBus = EventBusManager()
    private val keyValues = InMemoryKeyValues()
    private val viewModel by lazy { SearchViewModel({ emptyList() }, eventBus, RecentSearchStore(keyValues)) }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    /**
     * Regression: the screen held the event bus in a field nothing injected, so tapping a
     * result crashed the app
     */
    private suspend fun TestScope.assertClickGoesTo(
        item: SearchResultItem,
        expected: BusEvent,
        expectedAction: SearchViewModel.UiAction = SearchViewModel.UiAction.NavigateBack,
    ) {
        val sent = mutableListOf<BusEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { eventBus.eventBus.toList(sent) }

        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchResultClicked(item))

        assertEquals(listOf(expected), sent)
        assertEquals(expectedAction, viewModel.events.first())
    }

    @Test
    fun `tapping a page opens it and moves the file browser to its folder`() = runTest {
        assertClickGoesTo(
            SearchResultItem.Document("d", "page", emptyList()),
            BusEvent.CodeEditorBusEvent.RevealDocument("d"),
            SearchViewModel.UiAction.OpenDocument("d"),
        )
    }

    @Test
    fun `typing alone remembers nothing, tapping a result does`() = runTest {
        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchInputChanged("haft"))
        assertEquals(emptyList(), viewModel.uiState.value.recentSearches)

        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchResultClicked(SearchResultItem.Section("s", "folder")))
        assertEquals(listOf("haft"), viewModel.uiState.value.recentSearches)
    }

    @Test
    fun `submitting remembers the term, but not one that is too short`() = runTest {
        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchRequested("a"))
        assertEquals(emptyList(), viewModel.uiState.value.recentSearches)

        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchRequested("haft"))
        assertEquals(listOf("haft"), viewModel.uiState.value.recentSearches)
    }

    @Test
    fun `recent searches are only offered until the input changes`() = runTest {
        RecentSearchStore(keyValues).remember("haft")
        assertTrue(viewModel.uiState.value.showRecentSearches)

        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchInputChanged("h"))
        viewModel.onUiEvent(SearchViewModel.UiEvent.SearchInputChanged(""))
        assertFalse(viewModel.uiState.value.showRecentSearches)
    }

    @Test
    fun `tapping a recent search searches for it and makes it the latest`() = runTest {
        RecentSearchStore(keyValues).apply {
            remember("first")
            remember("second")
        }
        viewModel.onUiEvent(SearchViewModel.UiEvent.RecentSearchClicked("first"))
        assertEquals("first", viewModel.uiState.value.currentSearchFilter)
        assertEquals(listOf("first", "second"), viewModel.uiState.value.recentSearches)
        assertFalse(viewModel.uiState.value.showRecentSearches)
    }

    @Test
    fun `tapping a section goes to the section`() = runTest {
        assertClickGoesTo(SearchResultItem.Section("s", "folder"), BusEvent.CodeEditorBusEvent.GoToSection("s"))
    }

    @Test
    fun `tapping a resource goes to the resource`() = runTest {
        assertClickGoesTo(SearchResultItem.Resource("r", "image"), BusEvent.CodeEditorBusEvent.GoToResource("r"))
    }
}
