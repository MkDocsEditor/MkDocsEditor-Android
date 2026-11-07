package de.markusressel.mkdocseditor.feature.qr.domain

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.requests.DefaultBody
import com.github.kittinunf.result.Result
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocsrestclient.BackendNotConfiguredException
import de.markusressel.mkdocsrestclient.DummyMkDocsRestClient
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.qr.QrCodeModel
import de.markusressel.mkdocsrestclient.reference.ItemRefModel
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayInputStream
import java.net.ConnectException
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals

class ResolveQrCodeUseCaseTest {

    private val requests = mutableListOf<Pair<String, Boolean>>()

    private fun useCase(answer: Result<QrCodeModel, FuelError>) = ResolveQrCodeUseCase(
        object : IMkDocsRestClient by DummyMkDocsRestClient() {
            override suspend fun getQrCode(key: String, scan: Boolean): Result<QrCodeModel, FuelError> {
                requests += key to scan
                return answer
            }
        }
    )

    private fun httpError(status: Int, body: String) = Result.error(
        FuelError.wrap(
            RuntimeException("HTTP $status"),
            Response(
                url = URL("http://localhost/qr/wm/"),
                statusCode = status,
                body = DefaultBody.from({ ByteArrayInputStream(body.toByteArray()) }, { body.length.toLong() }),
            ),
        )
    )

    private fun item(id: String, path: String) = ItemRefModel(type = "document", id = id, path = path, name = path.substringAfterLast('/'))

    @Test
    fun `one page with the key opens`() = runTest {
        val lookup = useCase(Result.success(QrCodeModel(key = "wm", item = item("d1", "haus/wm.md"), title = "Waschmaschine")))("wm", scan = true)

        assertEquals(QrLookup.Found(QrPage("d1", "Waschmaschine", "haus/wm.md")), lookup)
        assertEquals(listOf("wm" to true), requests)
    }

    @Test
    fun `a key on several pages lists them`() = runTest {
        val body = """{"code":"qr-conflict","message":"x","details":{"key":"wm","items":[
            {"item":{"type":"document","id":"d1","path":"a.md","name":"a"},"title":"A"},
            {"item":{"type":"document","id":"d2","path":"b.md","name":"b"},"title":"B"}],"lastScannedAt":null}}"""

        val lookup = useCase(httpError(409, body))("wm", scan = true)

        assertEquals(QrLookup.Conflict(listOf(QrPage("d1", "A", "a.md"), QrPage("d2", "B", "b.md"))), lookup)
    }

    @Test
    fun `a key that left its page says where it was`() = runTest {
        val body = """{"code":"qr-gone","message":"x","details":{"key":"wm","label":"Keller","lastPath":"haus/wm.md","lastScannedAt":"2026-11-07T10:00:00Z"}}"""

        assertEquals(QrLookup.NoPage("haus/wm.md", "Keller"), useCase(httpError(410, body))("wm", scan = true))
    }

    @Test
    fun `a key no page ever had has no page`() = runTest {
        val body = """{"code":"not-found","message":"unknown key"}"""

        assertEquals(QrLookup.NoPage(), useCase(httpError(404, body))("wm", scan = true))
    }

    @Test
    fun `a server without QR codes answers 404 without a code`() = runTest {
        assertEquals(QrLookup.Unsupported, useCase(httpError(404, "404 page not found"))("wm", scan = true))
    }

    @Test
    fun `a missing session asks to sign in`() = runTest {
        val body = """{"code":"unauthorized","message":"x"}"""

        assertEquals(QrLookup.NotSignedIn, useCase(httpError(401, body))("wm", scan = true))
    }

    @Test
    fun `no server and no connection are told apart`() = runTest {
        assertEquals(QrLookup.NoBackend, useCase(Result.error(FuelError.wrap(BackendNotConfiguredException())))("wm", scan = true))
        assertEquals(QrLookup.Unreachable, useCase(Result.error(FuelError.wrap(ConnectException())))("wm", scan = true))
    }

    @Test
    fun `other errors say what went wrong`() = runTest {
        assertEquals(QrLookup.Failed(R.string.error_server), useCase(httpError(500, "{}"))("wm", scan = false))
        assertEquals(listOf("wm" to false), requests)
    }
}
