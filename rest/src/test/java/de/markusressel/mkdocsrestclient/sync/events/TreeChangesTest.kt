package de.markusressel.mkdocsrestclient.sync.events

import de.markusressel.mkdocsrestclient.AuthorizationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
private const val TIMEOUT_MS = 5_000L

@OptIn(ExperimentalCoroutinesApi::class)
class TreeChangesTest {

    private val server = ServerSocket(0)
    private val url = "ws://127.0.0.1:${server.localPort}/events/ws/"

    @AfterTest
    fun cleanup() {
        server.close()
    }

    /** Answers the next connection with what [answer] does; the socket stays open afterwards */
    private fun onConnection(answer: (Socket, headers: List<String>) -> Unit) {
        thread(isDaemon = true) {
            runCatching {
                val socket = server.accept()
                val lines = socket.getInputStream().bufferedReader()
                answer(socket, generateSequence { lines.readLine() }.takeWhile { it.isNotEmpty() }.toList())
            }
        }
    }

    private fun Socket.acceptUpgrade(headers: List<String>) {
        val key = headers.first { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }.substringAfter(":").trim()
        val accept = Base64.getEncoder()
            .encodeToString(MessageDigest.getInstance("SHA-1").digest((key + WEBSOCKET_GUID).toByteArray()))
        getOutputStream().write(
            ("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $accept\r\n\r\n").toByteArray()
        )
        getOutputStream().flush()
    }

    private fun assertRefusalEndsTheFlowQuietly() = runBlocking {
        var refused = 0

        val changes = withTimeout(TIMEOUT_MS) {
            treeChanges(url, AuthorizationProvider.NONE, onRefused = { refused++ }).toList()
        }

        assertEquals(1, refused)
        changes
    }

    @Test
    fun `a refused upgrade ends the flow without an exception`() {
        onConnection { socket, _ ->
            socket.getOutputStream().write("HTTP/1.1 401 Unauthorized\r\nContent-Length: 0\r\n\r\n".toByteArray())
            socket.getOutputStream().flush()
        }

        assertEquals(emptyList(), assertRefusalEndsTheFlowQuietly())
    }

    @Test
    fun `a close with 4001 ends the flow without an exception`() {
        onConnection { socket, headers ->
            socket.acceptUpgrade(headers)
            // a close frame with the code 4001
            socket.getOutputStream().write(byteArrayOf(0x88.toByte(), 2, 0x0F, 0xA1.toByte()))
            socket.getOutputStream().flush()
        }

        assertEquals(listOf(Unit), assertRefusalEndsTheFlowQuietly())
    }

    @Test
    fun `a server that went silent counts as disconnected`() {
        onConnection { socket, headers -> socket.acceptUpgrade(headers) }

        assertFailsWith<IOException> {
            runBlocking {
                withTimeout(TIMEOUT_MS) {
                    connectOnce(url, AuthorizationProvider.NONE, onRefused = {}, heartbeatIntervalMs = 10, serverTimeoutMs = 50)
                        .collect()
                }
            }
        }
    }

    @Test
    fun `a connection that stayed up starts over with the shortest wait`() = runTest {
        val connectedAt = mutableListOf<Long>()
        val connections = flow {
            connectedAt += currentTime
            if (connectedAt.size == 3) {
                emit(Unit)
                delay(30_000)
            }
            if (connectedAt.size < 4) throw IOException("gone")
        }

        connections.retryingWithGrowingDelay(now = { currentTime }).toList()

        assertEquals(listOf(0L, 1_000L, 3_000L, 34_000L), connectedAt)
    }

    @Test
    fun `a connection that closes at once does not shorten the wait`() = runTest {
        val connectedAt = mutableListOf<Long>()
        val connections = flow {
            connectedAt += currentTime
            emit(Unit)
            if (connectedAt.size < 4) throw IOException("gone")
        }

        connections.retryingWithGrowingDelay(now = { currentTime }).toList()

        assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), connectedAt)
    }

    @Test
    fun `only a tree change counts as a change`() {
        assertTrue(isTreeChanged("""{"type":"tree-changed"}"""))
        assertFalse(isTreeChanged("""{"type":"heartbeat"}"""))
        assertFalse(isTreeChanged("""{"type":"online","documents":[]}"""))
    }

    @Test
    fun `an unreadable message is no change`() {
        assertFalse(isTreeChanged("not json"))
    }

    @Test
    fun `waiting grows with each attempt, up to a limit`() {
        assertEquals(1_000, reconnectDelayMs(0))
        assertEquals(2_000, reconnectDelayMs(1))
        assertEquals(30_000, reconnectDelayMs(5))
        assertEquals(30_000, reconnectDelayMs(1_000))
    }
}
