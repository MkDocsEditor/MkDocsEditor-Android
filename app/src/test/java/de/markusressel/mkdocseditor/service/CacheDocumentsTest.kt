package de.markusressel.mkdocseditor.service

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.HttpException
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheDocumentsTest {

    private val stored = mutableMapOf<String, String>()
    private val requested = mutableListOf<String>()

    private fun failure(status: Int): Result<String, FuelError> = Result.error(
        FuelError.wrap(
            HttpException(status, "error"),
            Response(URL("http://backend.invalid"), statusCode = status),
        )
    )

    private suspend fun run(vararg answers: Pair<String, Result<String, FuelError>>): CacheOutcome {
        val byId = answers.toMap()
        return cacheDocuments(
            documentIds = byId.keys.toList(),
            getContent = { id -> requested += id; byId.getValue(id) },
            store = { id, text -> stored[id] = text },
        )
    }

    @Test
    fun `all documents are stored`() = runTest {
        assertEquals(CacheOutcome.Complete, run("a" to Result.success("A"), "b" to Result.success("B")))
        assertEquals(mapOf("a" to "A", "b" to "B"), stored)
    }

    @Test
    fun `a 401 ends the run so the remaining documents are not requested`() = runTest {
        assertEquals(
            CacheOutcome.NotSignedIn,
            run("a" to Result.success("A"), "b" to failure(401), "c" to Result.success("C")),
        )
        assertEquals(listOf("a", "b"), requested)
        assertEquals(mapOf("a" to "A"), stored)
    }

    @Test
    fun `another failure skips only that document`() = runTest {
        assertEquals(CacheOutcome.SomeFailed, run("a" to failure(500), "b" to Result.success("B")))
        assertEquals(mapOf("b" to "B"), stored)
    }

    @Test
    fun `a document that cannot be stored does not end the run`() = runTest {
        val result = cacheDocuments(
            documentIds = listOf("a", "b"),
            getContent = { Result.success("text") },
            store = { id, text -> if (id == "a") error("full") else stored[id] = text },
        )
        assertEquals(CacheOutcome.SomeFailed, result)
        assertEquals(mapOf("b" to "text"), stored)
    }

    @Test
    fun `no documents at all is a complete run`() = runTest {
        assertEquals(CacheOutcome.Complete, run())
    }

    @Test
    fun `a cancelled run stops before the next document`() = runTest {
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            cacheDocuments(
                documentIds = listOf("a", "b"),
                getContent = { id ->
                    requested += id
                    coroutineContext[Job]!!.cancel()
                    Result.success("text")
                },
                store = { id, text -> stored[id] = text },
            )
        }
        job.join()
        assertEquals(listOf("a"), requested)
    }
}
