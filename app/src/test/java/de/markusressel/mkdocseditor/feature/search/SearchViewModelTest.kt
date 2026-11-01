package de.markusressel.mkdocseditor.feature.search

import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val eventBus = EventBusManager()
    private val viewModel by lazy { SearchViewModel({ emptyList() }, eventBus) }

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
    fun `tapping a section goes to the section`() = runTest {
        assertClickGoesTo(SearchResultItem.Section("s", "folder"), BusEvent.CodeEditorBusEvent.GoToSection("s"))
    }

    @Test
    fun `tapping a resource goes to the resource`() = runTest {
        assertClickGoesTo(SearchResultItem.Resource("r", "image"), BusEvent.CodeEditorBusEvent.GoToResource("r"))
    }
}
