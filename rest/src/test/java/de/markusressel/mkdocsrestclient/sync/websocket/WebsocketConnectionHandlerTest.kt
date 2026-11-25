package de.markusressel.mkdocsrestclient.sync.websocket

import de.markusressel.mkdocsrestclient.AuthorizationProvider
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.net.ServerSocket
import java.util.concurrent.AbstractExecutorService
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
    fun `an upgrade that never gets an answer fails`() {
        // accepts the connection and then stays silent, like a proxy without a backend
        val silent = ServerSocket(0)
        val failed = CountDownLatch(1)
        val handler = WebsocketConnectionHandler(
            AuthorizationProvider { null },
            WebsocketConnectionHandler.newDefaultClient(upgradeTimeoutMs = 200),
        )
        handler.setListener(object : WebsocketConnectionListener {
            override fun onOpen() = Unit
            override fun onMessage(text: String) = Unit
            override fun onClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) = failed.countDown()
        })

        silent.use {
            handler.connect("ws://127.0.0.1:${silent.localPort}/")
            assertTrue(failed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `a connection that fails before connect returns is reported`() {
        // OkHttp runs the call on its own thread, so the failure can come before connect has kept the socket: when the
        // app may not use the network (Doze, background), connecting fails at once. Here it runs on the calling thread.
        val inline = object : AbstractExecutorService() {
            override fun execute(command: Runnable) = command.run()
            override fun shutdown() = Unit
            override fun shutdownNow() = emptyList<Runnable>()
            override fun isShutdown() = false
            override fun isTerminated() = false
            override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
        }
        val closed = ServerSocket(0).use { it.localPort }
        var failures = 0
        val handler = WebsocketConnectionHandler(
            AuthorizationProvider.NONE,
            OkHttpClient.Builder().dispatcher(Dispatcher(inline)).build(),
        )
        handler.setListener(object : WebsocketConnectionListener {
            override fun onOpen() = Unit
            override fun onMessage(text: String) = Unit
            override fun onClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) {
                failures++
            }
        })

        handler.connect("ws://127.0.0.1:$closed/")

        assertEquals(1, failures)
        assertFalse(handler.isConnected)
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
