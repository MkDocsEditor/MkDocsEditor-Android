package de.markusressel.mkdocsrestclient.sync.automerge

import androidx.annotation.MainThread
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.websocket.SocketEntityBase
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import de.markusressel.mkdocsrestclient.toEntity
import de.markusressel.mkdocsrestclient.toJson
import org.automerge.SyncState
import timber.log.Timber
import java.util.UUID

data class SyncMessageWrapper(
    val syncMessage: ByteArray
)


/**
 * Class used to manage document changes from client- and server.
 *
 * All document operations run on the UI thread, the same thread the editor changes its
 * text on. So the editor text never changes between reading the local edits and applying
 * remote ones, and remote changes can be handed to the editor as splices.
 */
class Automerge2DocumentSyncManager(
    hostname: String,
    port: Int,
    ssl: Boolean,
    basicAuthConfig: BasicAuthConfig?,
    private val documentId: String,
    private val onConnectionStatusChanged: (connected: Boolean, errorCode: Int?, throwable: Throwable?) -> Unit,
    private val onInitialText: (initialText: String) -> Unit,
    /**
     * Called with the new text and the splices (relative to the previous text)
     * that remote changes made to it.
     */
    private val onTextChanged: (newText: String, splices: List<TextSplice>) -> Unit,
    /**
     * A function to get a full copy of the current text on this client.
     * This method is intended for internal use only.
     */
    private val currentText: () -> String,
    var readOnly: Boolean = false,
) : WebsocketConnectionListener {

    private var document: AutomergeTextDocument? = null

    /**
     * Sync state of the current connection. Sync states are never reused across connections.
     */
    private var syncState = SyncState()

    val websocketUrl: String by lazy {
        val protocol = if (ssl) "wss" else "ws"
        "$protocol://$hostname:$port/document/$documentId/ws"
    }

    private val websocketConnectionHandler = WebsocketConnectionHandler(websocketUrl, basicAuthConfig)

    /**
     * The URL used for the websocket connection
     */
    val url: String
        get() = websocketConnectionHandler.url

    /**
     * @return true if a there is an open connection to the server, false otherwise
     */
    val isConnected: Boolean
        get() = websocketConnectionHandler.isConnected

    /**
     * Connect to the given URL
     */
    fun connect() {
        syncState = SyncState()
        websocketConnectionHandler.setListener(this)
        websocketConnectionHandler.connect()
    }

    /**
     * Trigger synchronization: applies the local edits to the document (as splices)
     * and sends what the server does not have yet.
     */
    @MainThread
    fun sync() {
        val document = document ?: return
        if (!readOnly) {
            document.applyLocalText(currentText())
        }
        sendSyncMessages(document)
    }

    /**
     * Sends sync messages until the sync protocol has nothing more to send.
     */
    @MainThread
    private fun sendSyncMessages(document: AutomergeTextDocument) {
        if (!isConnected) {
            return
        }
        while (true) {
            val message = document.generateSyncMessage(syncState) ?: break
            val request = SyncRequestEntity(
                requestId = UUID.randomUUID().toString(),
                documentId = documentId,
                document = document.document,
                syncMessageWrapper = SyncMessageWrapper(syncMessage = message),
            )
            websocketConnectionHandler.send(request.toJson())
        }
    }

    /**
     * Disconnect a from the server
     */
    fun disconnect(code: Int, reason: String) {
        websocketConnectionHandler.disconnect(code, reason)
    }

    override fun onConnectionChanged(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        onConnectionStatusChanged.invoke(connected, errorCode, throwable)
    }

    override fun onMessage(text: String) {
        runOnUiThread {
            try {
                processIncomingMessage(text)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    @MainThread
    private fun processIncomingMessage(text: String) {
        val entity: SocketEntityBase = text.toEntity()
        if (entity.documentId != documentId) {
            // ignore requests for other documents
            return
        }

        when (entity.type) {
            "initial-content" -> {
                val initialContentEntity: SyncRequestEntity = text.toEntity()
                val document = AutomergeTextDocument.fromDocument(initialContentEntity.document)
                this.document = document
                syncState = SyncState()
                onInitialText(document.text)
                sendSyncMessages(document)
            }

            "sync-request" -> {
                val document = document ?: return
                val syncRequest: SyncRequestEntity = text.toEntity()

                // bring pending local edits into the document first, so the splices
                // of the remote changes are relative to the text the editor shows
                if (!readOnly) {
                    document.applyLocalText(currentText())
                }
                val splices = document.receiveSyncMessage(syncState, syncRequest.syncMessageWrapper.syncMessage)
                if (splices.isNotEmpty()) {
                    onTextChanged(document.text, splices)
                }
                sendSyncMessages(document)
            }
        }
    }

    /**
     * Shutdown the websocket client (and all websockets)
     */
    fun shutdown() {
        websocketConnectionHandler.shutdown()
    }

}
