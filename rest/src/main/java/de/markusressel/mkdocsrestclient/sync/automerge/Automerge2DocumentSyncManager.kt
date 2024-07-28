package de.markusressel.mkdocsrestclient.sync.automerge

import androidx.annotation.WorkerThread
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.websocket.SocketEntityBase
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import de.markusressel.mkdocsrestclient.toEntity
import de.markusressel.mkdocsrestclient.toJson
import org.automerge.AmValue
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.SyncState
import timber.log.Timber
import java.util.LinkedList
import java.util.UUID

data class SyncMessageWrapper(
    val syncMessage: ByteArray
)


/**
 * Class used to manage document changes from client- and server.
 */
class Automerge2DocumentSyncManager(
    hostname: String,
    port: Int,
    ssl: Boolean,
    basicAuthConfig: BasicAuthConfig?,
    private val documentId: String,
    private val onConnectionStatusChanged: (connected: Boolean, errorCode: Int?, throwable: Throwable?) -> Unit,
    private val onInitialText: (initialText: String) -> Unit,
    private val onTextChanged: (newText: String, patches: LinkedList<diff_match_patch.Patch>) -> Unit,
    /**
     * A function to get a full copy of the current text on this client.
     * This method is intended for internal use only.
     */
    private val currentText: () -> String,
    var readOnly: Boolean = false,
) : WebsocketConnectionListener {

    private lateinit var document: Document
    private lateinit var documentTextObjectId: ObjectId


    val websocketUrl: String by lazy {
        val protocol = if (ssl) "wss" else "ws"
        "$protocol://$hostname:$port/document/$documentId/ws"
    }

    private val websocketConnectionHandler =
        WebsocketConnectionHandler(websocketUrl, basicAuthConfig)

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

    private var clientShadowIsReady = false

    /**
     * Connect to the given URL
     */
    fun connect() {
        websocketConnectionHandler.setListener(this)
        websocketConnectionHandler.connect()
    }

    init {
//        var text: ObjectId
//        document.startTransaction().use { tx ->
//            // Create a text object under the "text" key of the root map
//            text = tx.set(ObjectId.ROOT, "text", ObjectType.TEXT)
//            tx.spliceText(text, 0, 0, "Hello world")
//            tx.commit()
//        }
//
//
//        // save the document
//        val docBytes: ByteArray = document.save()
//
//
//        // Load the document
//        val doc2: Document = Document.load(docBytes)
//        println(doc2.text(text).get()) // Prints "Hello world"
//
//
//        doc2.startTransaction().use { tx ->
//            tx.spliceText(text, 5, 0, " beautiful")
//            tx.commit()
//        }
//
//
//        document.startTransaction().use { tx ->
//            tx.spliceText(text, 5, 0, " there")
//            tx.commit()
//        }
//
//
//        // Merge the changes
//        document.merge(doc2)
//
//
//        // Prints either "Hello there beautiful world" or "hello beautiful there world"
//        // depending on the actor IDs that were generated for each document.
//        println(document.text(text).get())


    }

    /**
     * Trigger synchronization
     */
    fun sync() {
        // TODO: figure out how to ignore user input... is it enough to literally ignore it?
        val newText = when {
            readOnly -> return
            else -> currentText()
        }

        sendPatch(newText = newText)
    }

    /**
     * Send a patch to the server
     *
     * @param newText the new and (presumably) changed text
     * @return id of the EditRequest sent to the server
     */
    private fun sendPatch(
        newText: String = currentText()
    ): String {
        // create temporary fork to make changes and merge them later
        val forkedDocument = document.fork()
        forkedDocument.startTransaction().use { tx ->
            // TODO: figure out how to replace the whole text instead of splicing it
            //  or find a way to actually splice changes in
            tx.spliceText(documentTextObjectId, 0, 0, newText)
            tx.commit()
        }
        // merge changes back into original document
        document.merge(forkedDocument)

        val outgoingSyncState = SyncState()
        val outgoingBytes = SyncMessageWrapper(syncMessage = document.generateSyncMessage(outgoingSyncState).get())

        // parse to json
        val requestId = UUID.randomUUID().toString()
        val editRequestModel = SyncRequestEntity(
            requestId = requestId,
            documentId = documentId,
            document = document,
            syncMessageWrapper = outgoingBytes,
        )

        // send to server
        val json = editRequestModel.toJson()

        websocketConnectionHandler.send(json)

        return requestId
    }

    /**
     * Disconnect a from the server
     */
    fun disconnect(code: Int, reason: String) {
        clientShadowIsReady = false
        websocketConnectionHandler.disconnect(code, reason)
    }

    override fun onConnectionChanged(connected: Boolean, errorCode: Int?, throwable: Throwable?) {
        if (!connected) clientShadowIsReady = false
        onConnectionStatusChanged.invoke(connected, errorCode, throwable)
    }

    override fun onMessage(text: String) {
        doAsync {
            try {
                processIncomingMessage(text)
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    @WorkerThread
    private fun processIncomingMessage(text: String) {
        val entity: SocketEntityBase = text.toEntity()
        if (entity.documentId != documentId) {
            // ignore requests for other documents
            return
        }

        when (entity.type) {
            "initial-content" -> {
                val initialContentEntity: SyncRequestEntity = text.toEntity()
                runOnUiThread {
                    document = initialContentEntity.document
                    document.applyEncodedChanges(initialContentEntity.syncMessageWrapper.syncMessage)

                    val text = (document.get(ObjectId.ROOT, "content").get() as AmValue.Text)
                    documentTextObjectId = text.id

//                    document.startTransaction().use { tx ->
//                        documentTextObjectId = tx.set(ObjectId.ROOT, "content", ObjectType.TEXT)
//                        tx.spliceText(documentTextObjectId, 0, 0, "Initial Text")
//                        tx.commit()
//                    }

                    val currentTextInDocument = document.text(documentTextObjectId).get()
                    onInitialText(currentTextInDocument)
//                    document.startTransaction().use { tx ->
//                        with(tx) {
//                            // Create a text object under the "text" key of the root map
//                            documentTextObjectId = set(ObjectId.ROOT, entity.documentId, ObjectType.TEXT)
//                            onInitialText(text(documentTextObjectId).get())
//                        }
//                    }
                }
            }

            "sync-request" -> {
                val syncRequest: SyncRequestEntity = text.toEntity()
//                documentTextObjectId = document.text(entity.documentId).get()
//                document.startTransaction().use { tx ->
//                    with(tx) {
//                        // Create a text object under the "text" key of the root map
//                        documentTextObjectId = set(ObjectId.ROOT, entity.documentId, ObjectType.TEXT)
//                        document.applyEncodedChanges(syncRequest.syncMessage)
//                        commit()
//                    }
//                }

                // parse and apply patches
                document.applyEncodedChanges(syncRequest.syncMessageWrapper.syncMessage)

                // patching has to be done on UI thread so the user can't type while the patch is applied
                runOnUiThread {
                    val currentTextInDocument = document.text(documentTextObjectId).get()
                    onTextChanged(currentTextInDocument, LinkedList())
                }
            }
        }
    }

    private fun resyncWithServer() {
        disconnect(1000, "Sync was broken, need to restart.")
        connect()
    }

    /**
     * Shutdown the websocket client (and all websockets)
     */
    fun shutdown() {
        clientShadowIsReady = false
        websocketConnectionHandler.shutdown()
    }

}
