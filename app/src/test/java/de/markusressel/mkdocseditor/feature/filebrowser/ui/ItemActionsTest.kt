package de.markusressel.mkdocseditor.feature.filebrowser.ui

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.requests.DefaultBody
import com.github.kittinunf.result.Result as FuelResult
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.LoadWikiTreeUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveItemUseCase
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.reference.MoveRequestModel
import de.markusressel.mkdocsrestclient.reference.MoveResultModel
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.StoredDocument
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItemActionsTest {

    private object NoStore : LocalDocumentStore {
        override fun load(documentId: String): StoredDocument? = null
        override fun save(documentId: String, document: StoredDocument) = Unit
        override fun ids(): List<String> = emptyList()
        override fun keepAside(documentId: String) = Unit
        override fun move(fromDocumentId: String, toDocumentId: String) = Unit
    }

    private fun fuelError(status: Int, body: String) = FuelError.wrap(
        HttpException(status, "error"),
        Response(
            url = URL("http://localhost/move/"),
            statusCode = status,
            body = DefaultBody.from({ ByteArrayInputStream(body.toByteArray()) }, { body.length.toLong() }),
        ),
    )

    private val moveResult = MoveResultModel(
        dryRun = false,
        type = ItemType.SECTION,
        oldId = "s",
        newId = "s2",
        oldPath = "a",
        newPath = "b/a",
        moved = emptyList(),
        documents = emptyList(),
        config = emptyList(),
    )

    private val item = WikiItem.Section(section("s", "a"))
    private val state = MutableStateFlow(UiState())
    private val messages = mutableListOf<String>()
    private var reloads = 0

    private fun actions(
        move: suspend (MoveRequestModel) -> FuelResult<MoveResultModel, FuelError> = { FuelResult.success(moveResult) },
        backlinks: suspend (WikiItem) -> Result<List<BacklinkModel>> = { Result.success(emptyList()) },
        tree: suspend () -> WikiTree? = { WikiTree(section("root", "root")) },
    ): ItemActions {
        val client = object : IMkDocsRestClient by DummyMkDocsRestClient() {
            override suspend fun move(request: MoveRequestModel) = move(request)
        }
        return ItemActions(
            state = state,
            moveItem = MoveItemUseCase(client, NoStore),
            getBacklinks = backlinks,
            loadTree = tree,
            showMessage = { messages += it },
            reload = { reloads++ },
        )
    }

    private val moveDialog get() = assertIs<DialogState.Move>(state.value.currentDialogState)

    @Test
    fun `moving loads the folders to choose from`() = runTest {
        actions().onActionClicked(item, ItemAction.Move)

        assertNotNull(moveDialog.tree)
        assertNull(moveDialog.error)
    }

    @Test
    fun `moving says when there are no folders to choose from`() = runTest {
        actions(tree = { null }).onActionClicked(item, ItemAction.Move)

        assertNull(moveDialog.tree)
        assertNotNull(moveDialog.error)
    }

    @Test
    fun `a preview comes before the move, and applying closes the dialog and reloads`() = runTest {
        val requests = mutableListOf<MoveRequestModel>()
        val actions = actions(move = { requests += it; FuelResult.success(moveResult) })
        actions.onActionClicked(item, ItemAction.Rename)

        actions.previewMove(null, "b")
        assertNotNull(moveDialog.preview)
        assertEquals(false, moveDialog.working)

        actions.applyMove()
        assertEquals(listOf(true, false), requests.map { it.dryRun })
        assertNull(state.value.currentDialogState)
        assertEquals(1, reloads)
        assertTrue(messages.single().startsWith("Now at /b/a"))
    }

    @Test
    fun `going back from a preview keeps the dialog`() = runTest {
        val actions = actions()
        actions.onActionClicked(item, ItemAction.Rename)
        actions.previewMove(null, "b")

        actions.backToTarget()

        assertNull(moveDialog.preview)
    }

    @Test
    fun `a failed move stays in the dialog with the reason`() = runTest {
        val body = """{"code":"conflict","message":"exists already"}"""
        val actions = actions(move = { FuelResult.error(fuelError(409, body)) })
        actions.onActionClicked(item, ItemAction.Rename)

        actions.previewMove(null, "b")

        assertEquals("exists already", moveDialog.error?.message)
        assertEquals(false, moveDialog.working)
        assertEquals(0, reloads)
    }

    @Test
    fun `applying without a preview does nothing`() = runTest {
        val requests = mutableListOf<MoveRequestModel>()
        val actions = actions(move = { requests += it; FuelResult.success(moveResult) })
        actions.onActionClicked(item, ItemAction.Rename)

        actions.applyMove()

        assertTrue(requests.isEmpty())
    }

    @Test
    fun `a late answer does not open a dialog the user closed`() = runTest {
        val answer = CompletableDeferred<Result<List<BacklinkModel>>>()
        val actions = actions(backlinks = { answer.await() })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { actions.onActionClicked(item, ItemAction.UsedBy) }
        assertIs<DialogState.Backlinks>(state.value.currentDialogState)

        state.update { it.copy(currentDialogState = null) }
        answer.complete(Result.success(emptyList()))
        job.join()

        assertNull(state.value.currentDialogState)
    }

    @Test
    fun `a late answer does not go into the dialog of another item`() = runTest {
        val answer = CompletableDeferred<Result<List<BacklinkModel>>>()
        val other = WikiItem.Section(section("other", "other"))
        val actions = actions(backlinks = { answer.await() })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { actions.onActionClicked(item, ItemAction.Delete) }

        state.update { it.copy(currentDialogState = DialogState.DeleteConfirmation(other)) }
        answer.complete(Result.success(emptyList()))
        job.join()

        assertEquals(DialogState.DeleteConfirmation(other), state.value.currentDialogState)
    }

    @Test
    fun `deleting says that the references could not be counted`() = runTest {
        actions(backlinks = { Result.failure(IllegalStateException()) }).onActionClicked(item, ItemAction.Delete)

        val dialog = assertIs<DialogState.DeleteConfirmation>(state.value.currentDialogState)
        assertTrue(dialog.usedByUnavailable)
    }

    @Test
    fun `the wiki tree comes from the cache when the server fails, and is null without both`() = runTest {
        val root = section("root", "root")
        assertNotNull(LoadWikiTreeUseCase(fresh = { error("down") }, cached = { root })())
        assertNull(LoadWikiTreeUseCase(fresh = { error("down") }, cached = { error("empty") })())
    }
}
