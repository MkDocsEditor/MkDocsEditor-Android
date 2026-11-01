package de.markusressel.mkdocsrestclient.sync.automerge

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.MainThread
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import timber.log.Timber

/**
 * Connects a [DocumentSyncSession] to the document websocket of the backend.
 *
 * Everything runs on the main thread: websocket messages are posted to it, so the
 * session and the editor never see concurrent changes. This class also does the timing
 * the protocol asks for: batched sync messages while typing, throttled presence,
 * heartbeats, reconnects with backoff, and debounced persistence of the local copy.
 */
@MainThread
class AutomergeDocumentSyncManager(
    private val hostname: String,
    private val port: Int,
    private val ssl: Boolean,
    authorizationProvider: AuthorizationProvider,
    documentId: String,
    store: LocalDocumentStore,
    private val listener: Listener,
) : SyncedDocument {

    interface Listener : DocumentSyncSession.Listener {
        /**
         * @param closeCode the websocket close code when the connection ended, see [CloseCode]
         */
        fun onConnectionStatusChanged(connected: Boolean, closeCode: Int? = null, throwable: Throwable? = null) {}
    }

    private val handler = Handler(Looper.getMainLooper())

    private val session = DocumentSyncSession(
        documentId = documentId,
        store = store,
        listener = object : DocumentSyncSession.Listener by listener {
            override fun onLocalCopyChanged() {
                schedulePersist()
                listener.onLocalCopyChanged()
            }

            override fun onDocumentRenamed(oldDocumentId: String, newDocumentId: String) {
                listener.onDocumentRenamed(oldDocumentId, newDocumentId)
                // the server closes with 4009, then we reconnect to the new id
            }
        },
    )

    private val connection = WebsocketConnectionHandler(authorizationProvider)

    /** true while the user of this manager wants to be connected */
    private var wantConnection = false
    private var reconnectAttempt = 0
    /** true after a 4003 close was answered with one reconnect (a changed role, docs/auth.md) */
    private var forbiddenRetried = false
    private var lastSentAt = 0L
    private var lastReceivedAt = 0L

    override val documentId: String
        get() = session.documentId

    override val text: String?
        get() = session.text

    override val version: DocumentVersion?
        get() = session.version

    override val hasLocalCopy: Boolean
        get() = session.hasLocalCopy

    override val readOnly: Boolean
        get() = session.readOnly

    override val isConnected: Boolean
        get() = connection.isConnected

    val isSyncing: Boolean
        get() = session.isSyncing

    private val websocketUrl: String
        get() {
            val protocol = if (ssl) "wss" else "ws"
            return "$protocol://$hostname:$port/document/${session.documentId}/ws/"
        }

    init {
        connection.setListener(object : WebsocketConnectionListener {
            override fun onOpen() = post {
                reconnectAttempt = 0
                lastReceivedAt = SystemClock.elapsedRealtime()
                session.onConnected(send = ::send)
                listener.onConnectionStatusChanged(true)
                scheduleHeartbeat()
            }

            override fun onMessage(text: String) = post {
                lastReceivedAt = SystemClock.elapsedRealtime()
                try {
                    session.onMessage(text)
                } catch (e: Exception) {
                    Timber.e(e, "Error handling sync message")
                }
            }

            override fun onClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) = post {
                onConnectionClosed(code, httpStatus, throwable)
            }
        })
    }

    /**
     * Connects (and keeps reconnecting) until [disconnect] is called
     */
    override fun connect() {
        wantConnection = true
        forbiddenRetried = false
        handler.removeCallbacks(reconnectRunnable)
        connection.connect(websocketUrl)
    }

    override fun disconnect(reason: String) = disconnect(CloseCode.NORMAL, reason)

    fun disconnect(code: Int, reason: String) {
        wantConnection = false
        handler.removeCallbacks(reconnectRunnable)
        handler.removeCallbacks(heartbeatRunnable)
        if (connection.isConnected) {
            session.flushSync()
        }
        connection.disconnect(code, reason)
        session.onDisconnected()
        listener.onConnectionStatusChanged(false)
        persistNow()
    }

    /**
     * Applies an edit of the editor to the local copy. Works offline; changes are sent batched.
     */
    override fun applyLocalEdit(
        base: DocumentVersion,
        baseText: String,
        splices: List<TextSplice>,
    ): DocumentVersion? {
        val applied = session.applyLocalEdit(base, baseText, splices)
        if (applied != null && !handler.hasCallbacks(syncRunnable)) {
            handler.postDelayed(syncRunnable, SYNC_BATCH_DELAY_MS)
        }
        return applied
    }

    override fun splicesSince(version: DocumentVersion, text: String): List<TextSplice> {
        return session.splicesSince(version, text)
    }

    /**
     * @param anchor UTF-16 index where the selection started, null if the editor has no focus
     * @param head UTF-16 index of the caret
     */
    override fun onLocalSelectionChanged(anchor: Int?, head: Int?) {
        pendingSelection = anchor to head
        if (!handler.hasCallbacks(presenceRunnable)) {
            handler.postDelayed(presenceRunnable, PRESENCE_THROTTLE_MS)
        }
    }

    /**
     * Writes the local copy to disk now. Call when the app goes to the background.
     */
    override fun persistNow() {
        handler.removeCallbacks(persistRunnable)
        try {
            session.persist()
        } catch (e: Exception) {
            Timber.e(e, "Could not persist document")
        }
    }

    /**
     * Disconnects and persists. The manager must not be used afterwards.
     */
    override fun shutdown() {
        disconnect(reason = "Closed")
        handler.removeCallbacksAndMessages(null)
    }

    private var pendingSelection: Pair<Int?, Int?>? = null

    private val syncRunnable = Runnable { session.flushSync() }

    private val presenceRunnable = Runnable {
        pendingSelection?.let { (anchor, head) -> session.updateLocalSelection(anchor, head) }
        pendingSelection = null
    }

    private val persistRunnable = Runnable { persistNow() }

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (!connection.isConnected) return
            val now = SystemClock.elapsedRealtime()
            if (now - lastReceivedAt > SERVER_TIMEOUT_MS) {
                Timber.w("No message from the server for ${now - lastReceivedAt} ms, reconnecting")
                connection.disconnect(CloseCode.HEARTBEAT_TIMEOUT, "Heartbeat timeout")
                onConnectionClosed(CloseCode.HEARTBEAT_TIMEOUT, null, null)
                return
            }
            if (now - lastSentAt >= HEARTBEAT_INTERVAL_MS) {
                session.sendHeartbeat()
            }
            scheduleHeartbeat()
        }
    }

    private val reconnectRunnable = Runnable {
        if (wantConnection) connection.connect(websocketUrl)
    }

    private fun send(message: String) {
        if (connection.send(message)) {
            lastSentAt = SystemClock.elapsedRealtime()
        }
    }

    private fun scheduleHeartbeat() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.postDelayed(heartbeatRunnable, HEARTBEAT_CHECK_MS)
    }

    private fun schedulePersist() {
        handler.removeCallbacks(persistRunnable)
        handler.postDelayed(persistRunnable, PERSIST_DEBOUNCE_MS)
    }

    private fun onConnectionClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) {
        handler.removeCallbacks(heartbeatRunnable)
        session.onDisconnected()
        persistNow()
        listener.onConnectionStatusChanged(false, code ?: httpStatus, throwable)

        if (!wantConnection) return
        if (code == null && (httpStatus == 401 || httpStatus == 403)) {
            // the upgrade was refused: reconnecting does not help until the user signs in again
            wantConnection = false
            return
        }
        when (code) {
            // not recoverable by reconnecting
            CloseCode.NORMAL, CloseCode.UNAUTHORIZED,
            CloseCode.NOT_FOUND, CloseCode.PROTOCOL_ERROR -> wantConnection = false

            // the role of the account changed: one reconnect applies the new role, a second
            // 4003 means the actor is really not allowed
            CloseCode.FORBIDDEN -> if (forbiddenRetried) {
                wantConnection = false
            } else {
                forbiddenRetried = true
                handler.post(reconnectRunnable)
            }

            // the session already switched to the new document id
            CloseCode.RENAMED -> handler.post(reconnectRunnable)

            else -> {
                val delay = (RECONNECT_BASE_DELAY_MS shl reconnectAttempt.coerceAtMost(5))
                    .coerceAtMost(RECONNECT_MAX_DELAY_MS)
                reconnectAttempt++
                handler.postDelayed(reconnectRunnable, delay)
            }
        }
    }

    private fun post(block: () -> Unit) {
        handler.post(block)
    }

    companion object {
        /** batch keystrokes into one sync message (protocol: 50 to 100 ms) */
        const val SYNC_BATCH_DELAY_MS = 75L

        /** at most one presence message every 100 ms */
        const val PRESENCE_THROTTLE_MS = 100L

        const val PERSIST_DEBOUNCE_MS = 1000L

        const val HEARTBEAT_INTERVAL_MS = 5_000L
        const val HEARTBEAT_CHECK_MS = 1_000L
        const val SERVER_TIMEOUT_MS = 15_000L

        const val RECONNECT_BASE_DELAY_MS = 1_000L
        const val RECONNECT_MAX_DELAY_MS = 30_000L
    }
}
