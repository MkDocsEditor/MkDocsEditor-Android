package de.markusressel.mkdocsrestclient.sync

import android.content.Context
import androidx.annotation.WorkerThread
import com.google.gson.Gson
import com.google.gson.JsonParseException
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.commons.android.material.toast
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.websocket.SocketEntityBase
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import timber.log.Timber
import java.util.*

/**
 * Class used to manage document changes from client- and server.
 */
class DocumentSyncManager(
        val context: Context,
        hostname: String,
        port: Int,
        ssl: Boolean,
        basicAuthConfig: BasicAuthConfig,
        private val documentId: String,
        private val onConnectionStatusChanged: (connected: Boolean, errorCode: Int?, throwable: Throwable?) -> Unit,
        private val onInitialText: (initialText: String) -> Unit,
        private val onTextChanged: (newText: String, patches: LinkedList<diff_match_patch.Patch>) -> Unit,
        private val currentText: () -> String) : WebsocketConnectionListener {

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

    private var clientShadowIsReady = false
    private var clientShadow: String = ""

    /**
     * Connect to the given URL
     */
    fun connect() {
        websocketConnectionHandler.setListener(this)
        websocketConnectionHandler.connect()
    }

    /**
     * Send a patch to the server
     *
     * @param previousText the text to use as the previous version
     * @param newText the new and (presumably) changed text
     * @return id of the EditRequest sent to the server
     */
    fun sendPatch(previousText: String = clientShadow, newText: String = currentText()): String? {
        if (!clientShadowIsReady) {
            context.toast("Shadow is not ready yet...")
            return null
        }

        // compute diff to current shadow
        val diffs = DIFF_MATCH_PATCH.diff_main(previousText, newText)
        // take a checksum of the client shadow before the diff has been applied
        val clientShadowChecksumBeforePatch = clientShadow.checksum()
        // update client shadow with the new text
        clientShadow = newText

        // create patch from diffs
        val patches = DIFF_MATCH_PATCH.patch_make(diffs)

        // parse to json
        val requestId = UUID.randomUUID().toString()
        val editRequestModel = EditRequestEntity(
                requestId = requestId,
                documentId = documentId,
                patches = DIFF_MATCH_PATCH.patch_toText(patches),
                shadowChecksum = clientShadowChecksumBeforePatch)

        // send to server
        websocketConnectionHandler.send(GSON.toJson(editRequestModel))

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
        val entity = GSON.fromJson(text, SocketEntityBase::class.java)
        if (entity.documentId != documentId) {
            // ignore requests for other documents
            return
        }

        when (entity.type) {
            "initial-content" -> {
                val initialContentEntity = GSON.fromJson(text, InitialContentRequestEntity::class.java)

                runOnUiThread {
                    clientShadow = initialContentEntity.content
                    onInitialText(initialContentEntity.content)
                }
                clientShadowIsReady = true
            }
            "edit-request" -> {
                val editRequest = GSON.fromJson(text, EditRequestEntity::class.java)
                        ?: throw JsonParseException("result was null!")

                // parse and apply patches
                val patches = DIFF_MATCH_PATCH.patch_fromText(editRequest.patches)

                // patching has to be done on UI thread so the user can't type while the patch is applied
                runOnUiThread {
                    // patch the clientShadow
                    if (!fragilePatchShadow(editRequest, patches)) {
                        Timber.e("The client shadow does not match the server shadow. A synchronization restart is necessary.")
                        context.toast("Shadow out of sync")
                        resyncWithServer()
                        return@runOnUiThread
                    }

                    val patchedText = fuzzyPatchCurrentText(patches)
                    onTextChanged(patchedText, patches)
                }
            }
        }
    }

    /**
     * Fuzzy patch the [currentText]
     */
    private fun fuzzyPatchCurrentText(patches: LinkedList<diff_match_patch.Patch>): String {
        val currentText = currentText()
        val patchResult = DIFF_MATCH_PATCH.patch_apply(patches, currentText)
        val patchedText = patchResult[0] as String

        // we don't update the clientShadow here, this is only done when the text is changed from the client
        return patchedText
    }

    /**
     * Fragile patch the current shadow.
     *
     * @return true if the patch was successful, false otherwise
     */
    private fun fragilePatchShadow(editRequest: EditRequestEntity, patches: LinkedList<diff_match_patch.Patch>): Boolean {
        // make sure current shadow matches the server shadow before the patch
        if (clientShadow.checksum() != editRequest.shadowChecksum) {
            return false
        }

        // fragile patch shadow
        val patchResult = DIFF_MATCH_PATCH.patch_apply(patches, clientShadow)
        val patchedText = patchResult[0] as String
        val patchesApplied = patchResult[1] as BooleanArray

        clientShadow = patchedText
        return true
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
     * Calculates a checksum of this [String] with the given algorithm.
     *
     * @param algorithm algorithm to use
     * @return the checksum
     */
    private fun String.checksum(algorithm: String = "MD5"): String {
        return "$length"
//      val md = MessageDigest.getInstance(algorithm)
//      return BigInteger(1, md.digest(toByteArray())).toString(16)
    }


    companion object {
        private val DIFF_MATCH_PATCH = diff_match_patch()
        private var GSON = Gson()
    }

}