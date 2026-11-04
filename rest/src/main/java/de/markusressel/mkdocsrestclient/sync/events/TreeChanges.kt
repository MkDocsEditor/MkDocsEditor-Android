package de.markusressel.mkdocsrestclient.sync.events

import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.moshi
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager.Companion.HEARTBEAT_INTERVAL_MS
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager.Companion.RECONNECT_BASE_DELAY_MS
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager.Companion.RECONNECT_MAX_DELAY_MS
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager.Companion.SERVER_TIMEOUT_MS
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.MessageEnvelope
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val HELLO = """{"type":"hello","client":"MkDocsEditor-Android"}"""
private const val HEARTBEAT = """{"type":"heartbeat"}"""
private const val TREE_CHANGED = "tree-changed"
private const val HTTP_UNAUTHORIZED = 401
private const val STABLE_CONNECTION_MS = RECONNECT_MAX_DELAY_MS

private val envelopes = moshi.adapter(MessageEnvelope::class.java)

/** @return true if this message of the events websocket says that the tree changed */
internal fun isTreeChanged(message: String): Boolean = try {
    envelopes.fromJson(message)?.type == TREE_CHANGED
} catch (e: Exception) {
    Timber.w(e, "Unreadable message of the events websocket")
    false
}

/** @return how long to wait before the given attempt to connect again */
internal fun reconnectDelayMs(attempt: Long): Long =
    (RECONNECT_BASE_DELAY_MS shl attempt.coerceAtMost(5).toInt()).coerceAtMost(RECONNECT_MAX_DELAY_MS)

/**
 * Emits whenever the tree on the server may have changed: on each `tree-changed` and after each connect, because changes
 * are lost while disconnected. Stays connected and reconnects until it is cancelled. When the server refuses the session
 * it completes instead of failing, because connecting again does not help until the user signs in.
 *
 * @param url the url of the events websocket, see docs/sync-protocol.md "Events websocket"
 * @param onRefused called on another thread, right before this flow completes because of a refused session
 */
fun treeChanges(url: String, authorizationProvider: AuthorizationProvider, onRefused: () -> Unit): Flow<Unit> =
    connectOnce(url, authorizationProvider, onRefused).retryingWithGrowingDelay()

/**
 * Collects again after each failure, each time a bit later. Only a connection that stayed up starts over with the shortest
 * wait: a server that accepts and closes at once must not get a new connection and tree fetch every second.
 */
internal fun <T> Flow<T>.retryingWithGrowingDelay(now: () -> Long = ::nowMs): Flow<T> = flow {
    var failures = 0L
    var workingSince: Long? = null
    emitAll(
        onEach { if (workingSince == null) workingSince = now() }.retryWhen { _, _ ->
            if (workingSince?.let { now() - it >= STABLE_CONNECTION_MS } == true) failures = 0
            workingSince = null
            delay(reconnectDelayMs(failures++))
            true
        }
    )
}

/** @param serverTimeoutMs a server this silent is gone, even if the socket doesn't know yet: it sends heartbeats itself */
internal fun connectOnce(
    url: String,
    authorizationProvider: AuthorizationProvider,
    onRefused: () -> Unit,
    heartbeatIntervalMs: Long = HEARTBEAT_INTERVAL_MS,
    serverTimeoutMs: Long = SERVER_TIMEOUT_MS,
): Flow<Unit> = callbackFlow {
    val connection = WebsocketConnectionHandler(authorizationProvider)
    val lastReceivedAt = AtomicLong(nowMs())
    connection.setListener(object : WebsocketConnectionListener {
        override fun onOpen() {
            lastReceivedAt.set(nowMs())
            connection.send(HELLO)
            trySend(Unit)
        }

        override fun onMessage(text: String) {
            lastReceivedAt.set(nowMs())
            if (isTreeChanged(text)) trySend(Unit)
        }

        override fun onClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) {
            if (code == CloseCode.UNAUTHORIZED || httpStatus == HTTP_UNAUTHORIZED) {
                onRefused()
                close()
            } else {
                close(IOException("Events websocket closed: $code", throwable))
            }
        }
    })
    connection.connect(url)
    val heartbeat = launch {
        while (isActive) {
            delay(heartbeatIntervalMs)
            if (connection.isConnected && nowMs() - lastReceivedAt.get() > serverTimeoutMs) {
                close(IOException("No message on the events websocket for $serverTimeoutMs ms"))
                return@launch
            }
            connection.send(HEARTBEAT)
        }
    }
    awaitClose {
        heartbeat.cancel()
        connection.disconnect(CloseCode.NORMAL, "Done")
    }
}

private fun nowMs() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
