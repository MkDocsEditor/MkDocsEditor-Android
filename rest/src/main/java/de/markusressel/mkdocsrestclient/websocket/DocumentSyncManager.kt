package de.markusressel.mkdocsrestclient.websocket

import com.github.salomonbrys.kotson.jsonObject
import com.google.gson.Gson
import com.google.gson.JsonParseException
import de.markusressel.commons.android.core.doAsync
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.websocket.diff.diff_match_patch
import okhttp3.*
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit

class DocumentSyncManager(
        private val url: String,
        private val basicAuthConfig: BasicAuthConfig,
        private val documentId: String,
        private val onConnectionStatusChanged: ((connected: Boolean, errorCode: Int?, throwable: Throwable?) -> Unit),
        private val onInitialText: ((initialText: String) -> Unit),
        private val onTextChanged: ((newText: String, patches: LinkedList<diff_match_patch.Patch>) -> Unit)) {

    private val client: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .connectTimeout(3, TimeUnit.SECONDS)
            //            .pingInterval(30, TimeUnit.SECONDS)
            .authenticator { route, response ->
                val credential = Credentials
                        .basic(basicAuthConfig.username, basicAuthConfig.password)

                response
                        .request()
                        .newBuilder()
                        .header("Authorization", credential)
                        .build()
            }
            .build()

    private var isConnected = false
    private var webSocket: WebSocket? = null
    private val listener = object : EchoWebSocketListenerBase() {

        override fun onOpen(webSocket: WebSocket, response: Response?) {
            super.onOpen(webSocket, response)
            isConnected = true
            onConnectionStatusChanged(isConnected, null, null)
        }

        override fun onMessage(webSocket: WebSocket, text: String?) {
            super.onMessage(webSocket, text)

            if (text == null) {
                return
            }

            doAsync {
                try {
                    val editRequest = gson.fromJson(text, EditRequestEntity::class.java)

                    if (editRequest.documentId != documentId) {
                        // ignore requests for other documents
                        return@doAsync
                    }

                    if (previouslySentPatches.containsKey(editRequest.requestId)) {
                        // remember if this edit request is the answer to a previously sent patch from us
                        previouslySentPatches.remove(editRequest.requestId)
                        return@doAsync
                    }

                    // parse and apply patches
                    val patches: LinkedList<diff_match_patch.Patch> = diffMatchPatch.patch_fromText(editRequest.patches) as LinkedList<diff_match_patch.Patch>
                    currentText = diffMatchPatch.patch_apply(patches, currentText)[0] as String

                    onTextChanged(currentText!!, patches)
                } catch (e: JsonParseException) {
                    currentText = text
                    onInitialText(text)
                }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            super.onClosed(webSocket, code, reason)
            isConnected = false
            onConnectionStatusChanged(isConnected, null, null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable?, response: Response?) {
            super.onFailure(webSocket, t, response)

            // we dont call onConnectionStatusChanged(false) here because
            isConnected = false

            Timber.e(t, "Websocket error")
            runOnUiThread {
                onConnectionStatusChanged(isConnected, response?.code(), t)
            }
        }
    }

    private val diffMatchPatch = diff_match_patch()
    private var previouslySentPatches: MutableMap<String, String> = mutableMapOf()

    var currentText: String? = null

    private var gson = Gson()

    /**
     * Connect to the given URL
     */
    fun connect() {
        if (isConnected) {
            Timber.w("Already connected")
            return
        }

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    /**
     * Send a patch to the server
     *
     * @return id of the EditRequest sent to the server
     */
    fun sendPatch(previousText: String, newText: String): String {
        // compute diff
        val diffs = diffMatchPatch.diff_main(previousText, newText)
        // create path from diffs
        val patches = diffMatchPatch.patch_make(diffs)

        // parse to json
        val requestId = UUID.randomUUID().toString()
        val editRequestModel = jsonObject(
                "requestId" to requestId,
                "documentId" to documentId,
                "patches" to diffMatchPatch.patch_toText(patches))

        // send to server
        webSocket?.send(editRequestModel.toString())

        // remember that this request has been sent
        previouslySentPatches[requestId] = "sent"

        return requestId
    }

    /**
     * Disconnect a websocket
     */
    fun disconnect(code: Int, reason: String) {
        webSocket?.close(code, reason)
        webSocket = null
    }

    /**
     * @return true if a there is an open connection to the server, false otherwise
     */
    fun isConnected(): Boolean {
        return isConnected
    }

    /**
     * Shutdown the websocket client (and all websockets)
     */
    fun shutdown() {
        client.dispatcher().executorService().shutdown()
    }

}