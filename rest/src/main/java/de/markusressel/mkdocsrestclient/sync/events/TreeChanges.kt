package de.markusressel.mkdocsrestclient.sync.events

import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.moshi
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.MessageEnvelope
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException

private const val HELLO = """{"type":"hello","client":"MkDocsEditor-Android"}"""
private const val HEARTBEAT = """{"type":"heartbeat"}"""
private const val TREE_CHANGED = "tree-changed"
private const val HEARTBEAT_INTERVAL_MS = 5_000L
private const val RECONNECT_BASE_DELAY_MS = 1_000L
private const val RECONNECT_MAX_DELAY_MS = 30_000L
private const val HTTP_UNAUTHORIZED = 401

private val envelopes = moshi.adapter(MessageEnvelope::class.java)

/** The server refused the session, so connecting again does not help until the user signs in */
internal class TreeChangesRefusedException : IOException("The server refused the events websocket")

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
 * are lost while disconnected. Stays connected and reconnects until it is cancelled or the server refuses the session.
 *
 * @param url the url of the events websocket, see docs/sync-protocol.md "Events websocket"
 */
fun treeChanges(url: String, authorizationProvider: AuthorizationProvider): Flow<Unit> =
    connectOnce(url, authorizationProvider).retryWhen { cause, attempt ->
        if (cause is TreeChangesRefusedException) return@retryWhen false
        delay(reconnectDelayMs(attempt))
        true
    }

private fun connectOnce(url: String, authorizationProvider: AuthorizationProvider): Flow<Unit> = callbackFlow {
    val connection = WebsocketConnectionHandler(authorizationProvider)
    connection.setListener(object : WebsocketConnectionListener {
        override fun onOpen() {
            connection.send(HELLO)
            trySend(Unit)
        }

        override fun onMessage(text: String) {
            if (isTreeChanged(text)) trySend(Unit)
        }

        override fun onClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) {
            val refused = code == CloseCode.UNAUTHORIZED || httpStatus == HTTP_UNAUTHORIZED
            close(if (refused) TreeChangesRefusedException() else IOException("Events websocket closed: $code", throwable))
        }
    })
    connection.connect(url)
    val heartbeat = launch {
        while (isActive) {
            delay(HEARTBEAT_INTERVAL_MS)
            connection.send(HEARTBEAT)
        }
    }
    awaitClose {
        heartbeat.cancel()
        connection.disconnect(CloseCode.NORMAL, "Done")
    }
}
