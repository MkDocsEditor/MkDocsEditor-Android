package de.markusressel.mkdocseditor.feature.filebrowser.ui

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.requests.DefaultBody
import com.github.kittinunf.result.Result as FuelResult
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DeleteOutcome
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
import kotlin.test.assertFailsWith
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
    private val messages = mutableListOf<BrowserMessage>()
    private var reloads = 0
    private val deletes = mutableListOf<Pair<String, Boolean>>()
    private val deleted = mutableListOf<WikiItem>()

    private fun actions(
        move: suspend (MoveRequestModel) -> FuelResult<MoveResultModel, FuelError> = { FuelResult.success(moveResult) },
        backlinks: suspend (WikiItem) -> Result<List<BacklinkModel>> = { Result.success(emptyList()) },
        tree: suspend () -> WikiTree? = { WikiTree(section("root", "root")) },
        delete: (force: Boolean) -> DeleteOutcome = { DeleteOutcome.Deleted },
        ownConnection: Boolean = false,
    ): ItemActions {
        val client = object : IMkDocsRestClient by DummyMkDocsRestClient() {
            override suspend fun move(request: MoveRequestModel) = move(request)
        }
        return ItemActions(
            state = DialogSlot({ state.value.currentDialogState }) { change ->
                state.update { it.copy(currentDialogState = change(it.currentDialogState)) }
            },
            moveItem = MoveItemUseCase(client, NoStore),
            getBacklinks = backlinks,
            loadTree = tree,
            showMessage = { messages += it },
            reload = { reloads++ },
            deleteItem = { item, force -> deletes += item.id to force; delete(force) },
            ownConnection = ownConnection,
            onDeleted = { deleted += it },
        )
    }

    private val moveDialog get() = assertIs<DialogState.Move>(state.value.currentDialogState)
    private val deleteDialog get() = assertIs<DialogState.DeleteConfirmation>(state.value.currentDialogState)

    @Test
    fun `a page open elsewhere is deleted only after a second confirmation`() = runTest {
        val actions = actions(delete = { force -> if (force) DeleteOutcome.Deleted else DeleteOutcome.InUse })
        actions.onActionClicked(item, ItemAction.Delete)

        actions.applyDelete()
        assertTrue(deleteDialog.openElsewhere)
        assertEquals(false, deleteDialog.working)
        assertTrue(deleted.isEmpty())

        actions.applyDelete()
        assertEquals(listOf("s" to false, "s" to true), deletes)
        assertNull(state.value.currentDialogState)
        assertEquals(listOf<WikiItem>(item), deleted)
        assertEquals(1, reloads)
    }

    @Test
    fun `the editor of the page deletes it with force, it is open there itself`() = runTest {
        val actions = actions(ownConnection = true)
        actions.onActionClicked(item, ItemAction.Delete, openElsewhere = false)
        assertEquals(false, deleteDialog.openElsewhere)

        actions.applyDelete()
        assertEquals(listOf("s" to true), deletes)
        assertEquals(listOf<WikiItem>(item), deleted)
    }

    @Test
    fun `the editor says up front that others have the page open`() = runTest {
        actions(ownConnection = true).onActionClicked(item, ItemAction.Delete, openElsewhere = true)
        assertTrue(deleteDialog.openElsewhere)
    }

    @Test
    fun `a failed delete closes the dialog and reports the error`() = runTest {
        val actions = actions(delete = { throw IllegalStateException("down") })
        actions.onActionClicked(item, ItemAction.Delete)

        assertFailsWith<IllegalStateException> { actions.applyDelete() }
        assertNull(state.value.currentDialogState)
        assertTrue(deleted.isEmpty())
    }

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
        assertTrue((messages.single() as BrowserMessage.Moved).newPath.startsWith("b/a"))
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
