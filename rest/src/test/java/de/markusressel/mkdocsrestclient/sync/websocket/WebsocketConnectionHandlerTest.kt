package de.markusressel.mkdocsrestclient.sync.websocket

import de.markusressel.mkdocsrestclient.AuthorizationProvider
import java.net.ServerSocket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebsocketConnectionHandlerTest {

    private val server = ServerSocket(0)
    private val url = "ws://127.0.0.1:${server.localPort}/"

    /** the header lines of the first request, then the connection is closed */
    private val requestHeaders = CompletableFuture<List<String>>().also { result ->
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    val lines = socket.getInputStream().bufferedReader()
                    result.complete(generateSequence { lines.readLine() }.takeWhile { it.isNotEmpty() }.toList())
                }
            }
        }
    }

    @AfterTest
    fun cleanup() {
        server.close()
    }

    @Test
    fun `the upgrade request carries the authorization`() {
        val handler = WebsocketConnectionHandler(AuthorizationProvider { "Bearer secret" })

        handler.connect(url)

        val headers = requestHeaders.get(5, TimeUnit.SECONDS)
        assertTrue("Authorization: Bearer secret" in headers, "$headers")
        assertTrue(headers.any { it.equals("Upgrade: websocket", ignoreCase = true) }, "$headers")
        handler.disconnect(1000, "done")
    }

    @Test
    fun `without an authorization the upgrade request has no such header`() {
        val handler = WebsocketConnectionHandler(AuthorizationProvider.NONE)

        handler.connect(url)

        val headers = requestHeaders.get(5, TimeUnit.SECONDS)
        assertFalse(headers.any { it.startsWith("Authorization", ignoreCase = true) }, "$headers")
        handler.disconnect(1000, "done")
    }

    @Test
    fun `connect does not wait for the authorization`() {
        val release = CountDownLatch(1)
        val handler = WebsocketConnectionHandler(AuthorizationProvider {
            release.await(5, TimeUnit.SECONDS)
            "Bearer secret"
        })

        val caller = thread { handler.connect(url) }
        caller.join(2_000)
        val returned = !caller.isAlive
        release.countDown()

        assertTrue(returned, "connect is blocked by the authorization")
        assertEquals(true, requestHeaders.get(5, TimeUnit.SECONDS).any { it == "Authorization: Bearer secret" })
        handler.disconnect(1000, "done")
    }
}
