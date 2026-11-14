package de.markusressel.mkdocseditor.feature.history.domain

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.auth.FakeAuthApi
import de.markusressel.mkdocseditor.auth.InMemoryTokenStore
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AuthSession
import de.markusressel.mkdocseditor.feature.auth.FakeAuthBackends
import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.history.ChangeAuthorModel
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DocumentChangesModel
import de.markusressel.mkdocsrestclient.history.SeenMarkModel
import de.markusressel.mkdocsrestclient.history.SeenModel
import kotlinx.coroutines.test.runTest
import java.net.URL
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeenChangesUseCaseTest {

    private val hunk = DiffHunkModel(heading = "Setup", oldStart = 1, oldLines = 1, newStart = 1, newLines = 1)
    private val alice = ChangeAuthorModel(accountId = "a1", username = "alice", displayName = "Alice")
    private val carol = ChangeAuthorModel(accountId = "a3", username = "carol", displayName = "Carol")

    private var answer: Result<DocumentChangesModel, FuelError> = Result.success(changes(listOf(alice)))
    private val seen = mutableListOf<String>()

    private fun changes(authors: List<ChangeAuthorModel>, since: SeenMarkModel? = SeenMarkModel(Date(0), "old")) =
        DocumentChangesModel(since = since, seenToken = "now", authors = authors, hunks = if (authors.isEmpty()) emptyList() else listOf(hunk))

    private val client = object : IMkDocsRestClient by DummyMkDocsRestClient() {
        override suspend fun getDocumentChanges(documentId: String, since: String?) = answer

        override suspend fun putDocumentSeen(documentId: String, seenToken: String): Result<SeenModel, FuelError> {
            seen += seenToken
            return Result.success(SeenModel(Date(), seenToken))
        }
    }

    private val useCase = SeenChangesUseCase(
        restClient = client,
        backends = FakeAuthBackends().apply {
            configs += backendConfig(1)
            selectedId = 1
        },
        authRepository = AuthRepository(
            InMemoryTokenStore().apply { put(1, AuthSession("t", null, "carol")) },
            { FakeAuthApi() },
            deviceName = "Pixel 8",
        ),
    )

    @Test
    fun `changes of someone else are told, and the page counts as seen with the token of the answer`() = runTest {
        val changes = useCase.open("d1")

        assertEquals(listOf(alice), changes?.authors)
        assertEquals(listOf("now"), seen)
    }

    @Test
    fun `the own edits are no news, those of an own agent are`() = runTest {
        answer = Result.success(changes(listOf(carol)))
        assertNull(useCase.open("d1"))

        answer = Result.success(changes(listOf(carol.copy(agents = listOf("Claude Code")))))
        assertEquals("Claude Code", useCase.open("d1")?.authors?.single()?.agents?.single())
    }

    @Test
    fun `the account itself is left out of the names, next to someone else`() = runTest {
        answer = Result.success(changes(listOf(carol, alice)))

        assertEquals(listOf(alice), useCase.open("d1")?.authors)
    }

    @Test
    fun `a page seen for the first time has no news, but is marked`() = runTest {
        answer = Result.success(DocumentChangesModel(since = null, seenToken = "now"))

        assertNull(useCase.open("d1"))
        assertEquals(listOf("now"), seen)
    }

    @Test
    fun `an unchanged page has no news`() = runTest {
        answer = Result.success(changes(emptyList()))

        assertNull(useCase.open("d1"))
    }

    @Test
    fun `a server without the endpoint tells nothing and marks nothing`() = runTest {
        answer = Result.error(FuelError.wrap(RuntimeException("404"), Response(URL("http://localhost/"), statusCode = 404)))

        assertNull(useCase.open("d1"))
        useCase.leave("d1")
        assertEquals(emptyList(), seen)
    }

    @Test
    fun `leaving the page marks it as it is now`() = runTest {
        useCase.leave("d1")

        assertEquals(listOf("now"), seen)
    }
}
