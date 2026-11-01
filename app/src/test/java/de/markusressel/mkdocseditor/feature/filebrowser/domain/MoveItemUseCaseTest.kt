package de.markusressel.mkdocseditor.feature.filebrowser.domain

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.requests.DefaultBody
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.doc
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTreeTest.Companion.section
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveItemUseCase
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.MoveOutcome
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.reference.MoveRequestModel
import de.markusressel.mkdocsrestclient.reference.MoveResultModel
import de.markusressel.mkdocsrestclient.reference.MovedItemModel
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.StoredDocument
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoveItemUseCaseTest {

    private class RecordingStore(private val failing: Set<String> = emptySet()) : LocalDocumentStore {
        val moves = mutableListOf<Pair<String, String>>()
        override fun load(documentId: String): StoredDocument? = null
        override fun save(documentId: String, document: StoredDocument) = Unit
        override fun ids(): List<String> = emptyList()
        override fun keepAside(documentId: String) = Unit
        override fun move(fromDocumentId: String, toDocumentId: String) {
            if (fromDocumentId in failing) error("disk full")
            moves += fromDocumentId to toDocumentId
        }
    }

    private fun client(answer: (MoveRequestModel) -> Result<MoveResultModel, FuelError>, requests: MutableList<MoveRequestModel>) =
        object : IMkDocsRestClient by DummyMkDocsRestClient() {
            override suspend fun move(request: MoveRequestModel): Result<MoveResultModel, FuelError> {
                requests += request
                return answer(request)
            }
        }

    /** a section with two documents moves; a resource moves without an id change */
    private fun result(dryRun: Boolean) = MoveResultModel(
        dryRun = dryRun,
        type = ItemType.SECTION,
        oldId = "s-old",
        newId = "s-new",
        oldPath = "a",
        newPath = "b/a",
        moved = listOf(
            MovedItemModel(ItemType.SECTION, "s-old", "s-new", "a", "b/a"),
            MovedItemModel(ItemType.DOCUMENT, "d-1", "d-1-new", "a/one.md", "b/a/one.md"),
            MovedItemModel(ItemType.DOCUMENT, "d-2", "d-2-new", "a/two.md", "b/a/two.md"),
            MovedItemModel(ItemType.RESOURCE, "r-1", "r-1-new", "a/x.png", "b/a/x.png"),
        ),
        documents = emptyList(),
        config = emptyList(),
    )

    private val item = WikiItem.Section(section("s-old", "a", documents = listOf(doc("d-1", "one"), doc("d-2", "two"))))

    @Test
    fun `a dry run sends the request and leaves the local copies alone`() = runTest {
        val store = RecordingStore()
        val requests = mutableListOf<MoveRequestModel>()
        val useCase = MoveItemUseCase(client({ Result.success(result(it.dryRun)) }, requests), store)

        val outcome = useCase(item, parentId = "s-b", name = null, dryRun = true)

        assertIs<MoveOutcome.Done>(outcome)
        assertEquals(listOf(MoveRequestModel(type = "section", id = "s-old", parent = "s-b", name = null, dryRun = true)), requests)
        assertTrue(store.moves.isEmpty())
    }

    @Test
    fun `a real move re-keys the local copies of moved documents`() = runTest {
        val store = RecordingStore()
        val useCase = MoveItemUseCase(client({ Result.success(result(it.dryRun)) }, mutableListOf()), store)

        useCase(item, parentId = "s-b", name = null, dryRun = false)

        assertEquals(listOf("d-1" to "d-1-new", "d-2" to "d-2-new"), store.moves)
    }

    @Test
    fun `a failing re-key does not stop the others or fail the move`() = runTest {
        val store = RecordingStore(failing = setOf("d-1"))
        val useCase = MoveItemUseCase(client({ Result.success(result(it.dryRun)) }, mutableListOf()), store)

        val outcome = useCase(item, parentId = "s-b", name = null, dryRun = false)

        assertIs<MoveOutcome.Done>(outcome)
        assertEquals(listOf("d-2" to "d-2-new"), store.moves)
    }

    private fun fuelError(status: Int, body: String) = FuelError.wrap(
        RuntimeException("HTTP $status"),
        Response(
            url = URL("http://localhost/move/"),
            statusCode = status,
            body = DefaultBody.from({ ByteArrayInputStream(body.toByteArray()) }, { body.length.toLong() }),
        ),
    )

    @Test
    fun `errors carry the status and the reason of the backend`() = runTest {
        val error = fuelError(409, """{"code":"conflict","message":"b/a exists","details":{"reason":"exists","path":"b/a"}}""")
        val useCase = MoveItemUseCase(client({ Result.error(error) }, mutableListOf()), RecordingStore())

        val outcome = useCase(item, parentId = "s-b", name = null, dryRun = true)

        assertEquals(MoveOutcome.Failed(409, "exists", "b/a exists"), outcome)
    }

    @Test
    fun `errors without a JSON body keep the message`() {
        val failed = MoveItemUseCase.failure(fuelError(502, "Bad Gateway"))
        assertEquals(502, failed.status)
        assertNull(failed.reason)
        assertTrue(failed.message.isNotEmpty())
    }

    @Test
    fun `an older backend without the endpoint is recognised`() {
        // the default 404 of the web framework has no error code
        assertEquals(MoveOutcome.Failed.UNSUPPORTED, MoveItemUseCase.failure(fuelError(404, """{"message":"Not Found"}""")).reason)
        // a 404 of the backend itself (unknown id) has one
        assertNull(MoveItemUseCase.failure(fuelError(404, """{"code":"not-found","message":"No item with id '1' found"}""")).reason)
    }
}
