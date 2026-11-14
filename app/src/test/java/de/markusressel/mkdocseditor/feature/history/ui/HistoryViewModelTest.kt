package de.markusressel.mkdocseditor.feature.history.ui

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.requests.DefaultBody
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.DiffState
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.HistoryEvent
import de.markusressel.mkdocseditor.feature.history.ui.HistoryViewModel.UiEvent
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DiffLineModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import de.markusressel.mkdocsrestclient.history.HistoryAuthorModel
import de.markusressel.mkdocsrestclient.history.HistoryDiffModel
import de.markusressel.mkdocsrestclient.history.HistoryEntryModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val anna = HistoryAuthorModel("anna", "anna@mkdocseditor.invalid", "anna", "a1")
    private fun entry(revision: String) = HistoryEntryModel(revision, Date(0), anna, emptyList(), "Update a.md", "a.md")

    private val hunk = DiffHunkModel(
        heading = "Setup", headingOccurrence = 1, oldStart = 3, oldLines = 1, newStart = 3, newLines = 1,
        lines = listOf(DiffLineModel(DiffOp.REMOVED, "a"), DiffLineModel(DiffOp.ADDED, "b")),
    )

    private var history: Result<List<HistoryEntryModel>, FuelError> = Result.success(listOf(entry("c"), entry("b"), entry("a")))
    private val diffRequests = mutableListOf<Pair<String, String>>()
    private val restored = mutableListOf<String>()
    private val reverted = mutableListOf<String>()
    private var revertAnswer: Result<HistoryEntryModel, FuelError> = Result.success(entry("d"))
    private var historyRequests = 0
    private val events = mutableListOf<HistoryEvent>()

    private val client = object : IMkDocsRestClient by DummyMkDocsRestClient() {
        override suspend fun getDocumentHistory(documentId: String, limit: Int): Result<List<HistoryEntryModel>, FuelError> {
            historyRequests++
            return history
        }

        override suspend fun getDocumentRevisionDiffHunks(documentId: String, from: String, to: String): Result<HistoryDiffModel, FuelError> {
            diffRequests += from to to
            return Result.success(HistoryDiffModel(from, to, listOf(hunk)))
        }

        override suspend fun revertDocumentRevision(documentId: String, revision: String): Result<HistoryEntryModel, FuelError> {
            reverted += revision
            return revertAnswer
        }

        override suspend fun restoreDocumentRevision(documentId: String, revision: String): Result<String, FuelError> {
            restored += revision
            return Result.success("")
        }
    }

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(canRestore: Boolean = true) = HistoryViewModel(client).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        viewModel.initialize("d1", canRestore, canRevert = true)
    }

    @Test
    fun `the history of the document is listed newest first`() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf("c", "b", "a"), viewModel.uiState.value.entries.map { it.revision })
        assertEquals(false, viewModel.uiState.value.loading)
    }

    @Test
    fun `a history that fails to load says why and loads again on retry`() = runTest {
        history = Result.error(FuelError.wrap(RuntimeException("404"), Response(URL("http://localhost/"), statusCode = 404)))
        val viewModel = viewModel()
        assertEquals(R.string.error_not_found, viewModel.uiState.value.problem)

        history = Result.success(listOf(entry("a")))
        viewModel.onUiEvent(UiEvent.RetryClicked)

        assertNull(viewModel.uiState.value.problem)
        assertEquals(listOf("a"), viewModel.uiState.value.entries.map { it.revision })
    }

    @Test
    fun `an entry shows the diff from the commit before it, and closes on a second tap`() = runTest {
        val viewModel = viewModel()

        viewModel.onUiEvent(UiEvent.EntryClicked("b"))

        assertEquals(listOf("a" to "b"), diffRequests)
        assertEquals(DiffState.Loaded(listOf(hunk)), viewModel.uiState.value.diffs["b"])
        assertEquals("b", viewModel.uiState.value.expanded)

        viewModel.onUiEvent(UiEvent.EntryClicked("b"))
        viewModel.onUiEvent(UiEvent.EntryClicked("b"))

        assertEquals(1, diffRequests.size, "a loaded diff is not asked for again")
    }

    @Test
    fun `the first version has nothing to compare with`() = runTest {
        val viewModel = viewModel()

        viewModel.onUiEvent(UiEvent.EntryClicked("a"))

        assertEquals(DiffState.FirstVersion, viewModel.uiState.value.diffs["a"])
        assertEquals(emptyList(), diffRequests)
    }

    @Test
    fun `a hunk opens the editor at its section`() = runTest {
        val viewModel = viewModel()

        viewModel.onUiEvent(UiEvent.HunkClicked(hunk))

        assertEquals(listOf<HistoryEvent>(HistoryEvent.OpenInEditor(ChangeLocation(listOf("Setup")))), events)
    }

    @Test
    fun `a version comes back only after confirming, and the history reloads`() = runTest {
        val viewModel = viewModel()
        viewModel.onUiEvent(UiEvent.RestoreClicked(entry("b")))
        assertEquals(emptyList(), restored)

        viewModel.onUiEvent(UiEvent.Confirmed)

        assertEquals(listOf("b"), restored)
        assertNull(viewModel.uiState.value.confirm)
        assertEquals(2, historyRequests)
        assertEquals(listOf<HistoryEvent>(HistoryEvent.Message(R.string.history_restored)), events)
    }

    @Test
    fun `a dismissed restore changes nothing`() = runTest {
        val viewModel = viewModel()
        viewModel.onUiEvent(UiEvent.RestoreClicked(entry("b")))

        viewModel.onUiEvent(UiEvent.ConfirmDismissed)
        viewModel.onUiEvent(UiEvent.Confirmed)

        assertEquals(emptyList(), restored)
    }

    private fun conflict(body: String) = Result.error(
        FuelError.wrap(
            RuntimeException("409"),
            Response(
                url = URL("http://localhost/"),
                statusCode = 409,
                body = DefaultBody.from({ ByteArrayInputStream(body.toByteArray()) }, { body.length.toLong() }),
            ),
        )
    )

    @Test
    fun `a change is undone after confirming, and the history reloads`() = runTest {
        val viewModel = viewModel()
        viewModel.onUiEvent(UiEvent.UndoClicked(entry("b")))
        assertEquals(emptyList(), reverted)

        viewModel.onUiEvent(UiEvent.Confirmed)

        assertEquals(listOf("b"), reverted)
        assertEquals(emptyList(), restored)
        assertEquals(2, historyRequests)
        assertEquals(listOf<HistoryEvent>(HistoryEvent.Message(R.string.history_undone)), events)
    }

    @Test
    fun `an undo in the way of later changes names their sections`() = runTest {
        // the answer of a real backend (MkDocsEditor-Backend#99)
        revertAnswer = conflict(
            """{"name":"Conflict","code":"revert-conflict","message":"later edits changed the same lines, edit the document by hand",""" +
                """"details":{"conflicts":[{"heading":"Setup","headingOccurrence":1,"line":3}],"headings":["Setup"]}}"""
        )
        val viewModel = viewModel()
        viewModel.onUiEvent(UiEvent.UndoClicked(entry("b")))
        viewModel.onUiEvent(UiEvent.Confirmed)

        assertEquals(listOf<HistoryEvent>(HistoryEvent.UndoConflict(listOf("Setup"))), events)
        assertEquals(1, historyRequests)
    }

    @Test
    fun `an undo of a change that is gone says so`() = runTest {
        revertAnswer = conflict("""{"name":"Conflict","code":"nothing-to-revert","message":"the change of this revision is not in the document anymore"}""")
        val viewModel = viewModel()
        viewModel.onUiEvent(UiEvent.UndoClicked(entry("b")))
        viewModel.onUiEvent(UiEvent.Confirmed)

        assertEquals(listOf<HistoryEvent>(HistoryEvent.Message(R.string.history_undo_nothing)), events)
    }
}
