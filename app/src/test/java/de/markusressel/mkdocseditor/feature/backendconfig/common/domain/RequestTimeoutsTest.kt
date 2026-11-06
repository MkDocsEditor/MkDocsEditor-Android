package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import android.app.Application
import com.github.kittinunf.fuel.core.Method
import de.markusressel.mkdocsrestclient.CONNECT_TIMEOUT_MS
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.RequestKind
import de.markusressel.mkdocsrestclient.RequestManager
import de.markusressel.mkdocsrestclient.reference.MoveRequestModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A request may only fail for taking long when nothing waits for its result: the quick reads. A long action, like a
 * move, waits for a server that is still working on it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class RequestTimeoutsTest {

    private val server = TestHttpServer()
    private val requestManager = RequestManager(baseUrl = { "http://127.0.0.1:${server.port}/" })
    private val client = MkDocsRestClient(requestManager)

    @After
    fun stopServer() {
        server.stop()
    }

    @Test
    fun `every kind waits as long for the connection, and as long for an answer as it needs`() {
        val read = RequestKind.entries.associateWith {
            val options = requestManager.createRequest("section/", method = Method.GET, kind = it).executionOptions
            assertEquals(CONNECT_TIMEOUT_MS, options.timeoutInMillisecond)
            options.timeoutReadInMillisecond
        }

        assertEquals(3_000, CONNECT_TIMEOUT_MS, "a server that doesn't take the connection fails soon")
        assertEquals(10_000, read.getValue(RequestKind.Quick), "a quick read gives up after 10 s")
        assertTrue(read.getValue(RequestKind.Blocking) <= 5_000, "a lookup a screen waits for gives up soon")
        assertTrue(read.getValue(RequestKind.LongAction) >= 5 * 60_000, "a long action waits for minutes")
        assertTrue(read.getValue(RequestKind.Transfer) >= 30_000, "a transfer may stall for a moment")
    }

    @Test
    fun `an upload waits like a transfer`() {
        val options = requestManager.createUploadRequest("resource/root/a.png/").executionOptions
        assertEquals(CONNECT_TIMEOUT_MS, options.timeoutInMillisecond)
        assertEquals(RequestKind.Transfer.readTimeoutMs, options.timeoutReadInMillisecond)
    }

    @Test
    fun `a quick read waits for a server that needs a few seconds`() = runBlocking<Unit> {
        server.body = "{}"
        server.delayMs = 4_000L

        assertNull(requestManager.doRequest<String>("section/", Method.GET).component2())
    }

    /**
     * The bug: the 60 s of a move were its connect timeout, so it failed after the default read timeout of Fuel (15 s),
     * while the server went on with it
     */
    @Test
    fun `a move succeeds on a server that works on it longer than a quick read may take`() = runBlocking<Unit> {
        server.body = MOVE_RESULT
        server.delayMs = FUEL_DEFAULT_READ_TIMEOUT_MS + 1_000L

        val move = client.move(MoveRequestModel(type = "section", id = "guide", parent = "archive", dryRun = false))
        val lookup = client.getMoved(listOf("guide"))

        assertNull(move.component2())
        assertEquals("archive/guide", assertNotNull(move.component1()).newPath)
        assertIs<SocketTimeoutException>(assertNotNull(lookup.component2()).exception)
    }

    @Test
    fun `an upload reports its progress up to the whole size`() = runBlocking<Unit> {
        val content = ByteArray(3 * 1024 * 1024) { it.toByte() }
        val reported = mutableListOf<Pair<Long, Long>>()

        val result = client.uploadResource("root", "big.bin", content) { done, total ->
            synchronized(reported) { reported += done to total }
        }

        assertNull(result.component2())
        val total = reported.last().second
        assertTrue(total >= content.size, "the size of the whole request")
        assertEquals(total, reported.last().first)
        assertTrue(reported.size > 10, "progress while the data moves")
        assertEquals(reported.map { it.first }.sorted(), reported.map { it.first })
        assertTrue(server.receivedBodySizes.single() >= content.size)
    }

    /** The bug: without a file name the backend read the part as a form value, and refused a large file with 400 */
    @Test
    fun `an upload sends the content as a file`() = runBlocking<Unit> {
        client.uploadResource("root", "big.bin", ByteArray(10))

        assertTrue("filename=\"big.bin\"" in server.receivedBodyStarts.single(), server.receivedBodyStarts.single())
    }

    private companion object {
        const val FUEL_DEFAULT_READ_TIMEOUT_MS = 15_000L

        const val MOVE_RESULT = """{"dryRun": false, "type": "section", "oldId": "guide", "newId": "archive/guide",
            "oldPath": "guide", "newPath": "archive/guide", "moved": [], "documents": [], "config": []}"""
    }
}
