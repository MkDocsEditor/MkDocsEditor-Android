package de.markusressel.mkdocseditor.feature.history.domain

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.history.BlameBlockModel
import de.markusressel.mkdocsrestclient.history.BlameModel
import kotlinx.coroutines.test.runTest
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

class LineAuthorUseCaseTest {

    private val bob = FeedAuthorModel(accountId = "a2", username = "bob", displayName = "Bob", color = "#2f9e44")
    private val committed = BlameBlockModel(start = 1, end = 3, revision = "r1", author = bob)
    private val nobody = BlameBlockModel(start = 4, end = 4, pending = true, author = null)
    private var answer: Result<BlameModel, FuelError> =
        Result.success(BlameModel(revision = "h", lines = 4, blocks = listOf(committed, nobody)))

    private val client = object : IMkDocsRestClient by DummyMkDocsRestClient() {
        override suspend fun getDocumentBlame(documentId: String): Result<BlameModel, FuelError> = answer
    }
    private val lineAuthor = LineAuthorUseCase(client)

    @Test
    fun `the editor's first line is the first line of the blame`() = runTest {
        assertEquals(LineAuthorUseCase.Answer.Found(committed), lineAuthor("d1", 0))
        assertEquals(LineAuthorUseCase.Answer.Found(committed), lineAuthor("d1", 2))
    }

    @Test
    fun `a line nobody known wrote, or newer than the blame, is unknown`() = runTest {
        assertEquals(LineAuthorUseCase.Answer.Unknown, lineAuthor("d1", 3))
        assertEquals(LineAuthorUseCase.Answer.Unknown, lineAuthor("d1", 4))
    }

    @Test
    fun `a failed request says why`() = runTest {
        answer = Result.error(FuelError.wrap(RuntimeException("404"), Response(URL("http://localhost/"), statusCode = 404)))

        assertEquals(LineAuthorUseCase.Answer.Failed(R.string.error_not_found), lineAuthor("d1", 0))
    }
}
