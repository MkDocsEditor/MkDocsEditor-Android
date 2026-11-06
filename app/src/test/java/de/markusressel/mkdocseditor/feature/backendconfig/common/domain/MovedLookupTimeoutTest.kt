package de.markusressel.mkdocseditor.feature.backendconfig.common.domain

import android.app.Application
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.RequestManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Opening a document without a local copy first asks the server whether it moved. A coroutine timeout can not end the
 * blocking request, so the request has to end itself: before, a server that hung kept the editor blank for 15 s.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [36])
class MovedLookupTimeoutTest {

    /** accepts connections and never answers, like a server or a proxy that hangs */
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val held = Collections.synchronizedList(mutableListOf<Socket>())

    init {
        thread(isDaemon = true) {
            try {
                while (true) held += server.accept()
            } catch (_: IOException) {
                // closed at the end of the test
            }
        }
    }

    @After
    fun stopServer() {
        server.close()
        held.forEach { it.close() }
    }

    @Test
    fun `looking up moves gives up soon on a server that does not answer`() = runBlocking<Unit> {
        val client = MkDocsRestClient(RequestManager(baseUrl = { "http://127.0.0.1:${server.localPort}/" }))

        val startedAt = System.nanoTime()
        val failure = client.getMoved(listOf("page")).component2()
        val tookMs = (System.nanoTime() - startedAt) / 1_000_000

        assertNotNull(failure)
        assertTrue(tookMs < 6_000, "took $tookMs ms")
    }
}
