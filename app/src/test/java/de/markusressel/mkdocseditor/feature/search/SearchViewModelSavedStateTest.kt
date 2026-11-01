package de.markusressel.mkdocseditor.feature.search

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.search.data.RecentSearchStore
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Saved state is a Bundle, so this needs Robolectric */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class, sdk = [36])
class SearchViewModelSavedStateTest {

    private val dispatcher = StandardTestDispatcher()
    private val result = SearchResultItem.Section("s", "folder")

    private fun viewModel(handle: SavedStateHandle) = SearchViewModel(
        { listOf(result) },
        EventBusManager(),
        RecentSearchStore(InMemoryKeyValues()),
        handle,
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the search and its results are back after process death`() = runTest(dispatcher) {
        val handle = SavedStateHandle()
        viewModel(handle).onUiEvent(SearchViewModel.UiEvent.SearchInputChanged("haft"))
        testScheduler.runCurrent()

        val restored = viewModel(
            SavedStateHandle.createHandle(handle.savedStateProvider().saveState(), null)
        )
        testScheduler.advanceTimeBy(301)
        testScheduler.runCurrent()

        val state = restored.uiState.value
        assertEquals("haft", state.currentSearchFilter)
        assertEquals(listOf<SearchResultItem>(result), state.currentSearchResults)
        assertFalse(state.showRecentSearches)
    }
}
