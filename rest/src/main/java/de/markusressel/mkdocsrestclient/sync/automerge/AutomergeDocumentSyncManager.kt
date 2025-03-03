package de.markusressel.mkdocsrestclient.sync.automerge

import androidx.annotation.WorkerThread
import com.github.ajalt.timberkt.d
import com.github.kittinunf.fuel.util.decodeBase64
import com.github.kittinunf.fuel.util.encodeBase64
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.websocket.SocketEntityBase
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import de.markusressel.mkdocsrestclient.toEntity
import de.markusressel.mkdocsrestclient.toJson
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.PatchLog
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID


/**
 * Class used to manage document changes from client- and server.
 */
class AutomergeDocumentSyncManager(
    hostname: String,
    port: Int,
    ssl: Boolean,
    basicAuthConfig: BasicAuthConfig?,
    private val documentId: String,
    private val onConnectionStatusChanged: (connected: Boolean, errorCode: Int?, throwable: Throwable?) -> Unit,
    private val onInitialText: (initialText: String) -> Unit,
    private val onTextChanged: (newText: String) -> Unit,
    /**
     * A function to get a full copy of the current text on this client.
     * This method is intended for internal use only.
     */
    private val currentText: () -> String,
    var readOnly: Boolean = false,
) : WebsocketConnectionListener {

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
    private var clientShadow: Document = Document()

    init {
        // Create an object
        val doc: Document = Document()

        var text: ObjectId
        doc.startTransaction().use { tx ->
            // Create a text object under the "text" key of the root map
            text = tx.set(ObjectId.ROOT, "text", ObjectType.TEXT)
            tx.spliceText(text, 0, 0, "Hello world")
            tx.commit()
        }


        // save the document
        val docBytes: ByteArray = doc.save()


        // Load the document
        val doc2: Document = Document.load(docBytes)
        System.out.println(doc2.text(text).get()) // Prints "Hello world"

        doc2.startTransaction().use { tx ->
            tx.spliceText(text, 5, 0, " beautiful")
            tx.commit()
        }

        doc.startTransaction().use { tx ->
            tx.spliceText(text, 5, 0, " there")
            tx.commit()
        }


        // Merge the changes
        doc.merge(doc2)


        // Prints either "Hello there beautiful world" or "hello beautiful there world"
        // depending on the actor IDs that were generated for each document.
        System.out.println(doc.text(text).get())
    }

    /**
     * Connect to the given URL
     */
    fun connect() {
        websocketConnectionHandler.setListener(this)
        websocketConnectionHandler.connect()
    }

    /**
     * Trigger synchronization
     */
    fun sync() {
        if (!clientShadowIsReady) {
            return
        }

        // prevent client side changes by assuming a non-changed state
        val newText = when {
            readOnly -> clientShadow
            else -> currentText()
        }
        sendPatch(newText = newText)
    }

    /**
     * Send a patch to the server
     *
     * @param previousText the text to use as the previous version
     * @param newText the new and (presumably) changed text
     * @return id of the EditRequest sent to the server
     */
    private fun sendPatch(
        previousText: String = clientShadow.getContent(),
        newText: String = currentText()
    ): String {
        // compute diff to current shadow
        val diffs = DIFF_MATCH_PATCH.diff_main(previousText, newText)
        val patches = DIFF_MATCH_PATCH.patch_make(diffs)

        clientShadow.startTransaction().use { tx ->
            val text = clientShadow.createContentTextId()
            diffs.forEach { diff ->
                tx.spliceText(text, 0, 0, newText)
                tx.commit()
            }
        }

        // parse to json
        val requestId = UUID.randomUUID().toString()
        val editRequestModel = AutomergeSyncRequestEntity(
            requestId = requestId,
            documentId = documentId,
            documentState = clientShadow.save().encodeBase64().toString(),
            syncMessage =,
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
                val initialContentEntity: AutomergeSyncRequestEntity = text.toEntity()

                val decodedDocumentState = initialContentEntity.documentState.decodeBase64()
                val decodedSyncMessage = initialContentEntity.syncMessage.decodeBase64()

                Timber.d { "Initial content:${decodedDocumentState} ${decodedSyncMessage}" }

                val document = Document.load(decodedDocumentState)
                val content = document.getContent()

                runOnUiThread {
                    clientShadow = document
                    onInitialText(content)
                }
                clientShadowIsReady = true
            }

            "edit-request" -> {
                val editRequest: AutomergeSyncRequestEntity = text.toEntity()

                val decodedDocumentState = editRequest.documentState.decodeBase64()
                val document = Document.load(decodedDocumentState)


                // patching has to be done on UI thread so the user can't type while the patch is applied
                runOnUiThread {
                    // patch the clientShadow
                    val patchLog = patchShadow(document)
                    val content = document.getContent()
                    onTextChanged(content)
                }
            }
        }
    }

    private fun Document.createContentTextId(): ObjectId {
        return startTransaction().use { tx ->
            // Create a text object under the "text" key of the root map
            tx.set(ObjectId.ROOT, CONTENT_PATH, ObjectType.TEXT)
        }
    }

    private fun Document.getContent(): String {
        val contentTextId = createContentTextId()
        return text(contentTextId).get()
    }

    /**
     * Fragile patch the current shadow.
     *
     * @return true if the patch was successful, false otherwise
     */
    private fun patchShadow(
        otherDocument: Document,
    ): PatchLog {
        val patchLog = PatchLog()
        clientShadow.merge(otherDocument, patchLog)
        return patchLog
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

    /**
     * Calculates a checksum of this [String].
     *
     * Important notes for the implementation of this method:
     * - the text that is hashed must be encoded using UTF-16LE without BOM
     *   this will ensure the bytes are the same on all clients
     * - the checksum must include leading zeros
     * - all characters are lowercase
     *
     * @return the checksum
     */
    private fun String.checksum(): String {
        val md = MessageDigest.getInstance(CHECKSUM_ALGORITHM)
        val checksumByteArray = md.digest(toByteArray(charset = StandardCharsets.UTF_16LE))
        return checksumByteArray.toHexString()
    }

    /**
     * Converts a byte array to it's hex representation include leading zeros.
     */
    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        indices.forEach { i ->
            chars[i * 2] = HEX_DIGITS[this[i].toInt() shr (4) and 0xf]
            chars[i * 2 + 1] = HEX_DIGITS[this[i].toInt() and 0xf]
        }
        return String(chars)
    }

    companion object {
        private val DIFF_MATCH_PATCH = diff_match_patch()

        private const val CONTENT_PATH = "content"

        private const val CHECKSUM_ALGORITHM = "MD5"
        private val HEX_DIGITS = "0123456789abcdef".lowercase().toCharArray()
    }

}