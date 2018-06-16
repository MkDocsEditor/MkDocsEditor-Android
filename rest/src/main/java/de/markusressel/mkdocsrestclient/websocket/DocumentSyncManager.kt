package de.markusressel.mkdocsrestclient.websocket

import android.os.AsyncTask
import android.util.Log
import com.github.salomonbrys.kotson.jsonObject
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.websocket.diff.diff_match_patch
import okhttp3.*
import java.util.concurrent.TimeUnit

class DocumentSyncManager(private val url: String, private val basicAuthConfig: BasicAuthConfig, private val documentId: String, private val onInitialText: ((initialText: String) -> Unit), private val onTextChanged: ((newText: String) -> Unit), private val onError: ((code: Int?, throwable: Throwable?) -> Unit)) {

    private val client: OkHttpClient = OkHttpClient
            .Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
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

    private var webSocket: WebSocket? = null
    private var isInitialMessage = true

    /**
     * Connect to the given URL
     */
    fun connect() {
        val request = Request
                .Builder()
                .url(url)
                .build()

        val listener = object : EchoWebSocketListenerBase() {
            override fun onMessage(webSocket: WebSocket, text: String?) {
                super
                        .onMessage(webSocket, text)

                if (isInitialMessage) {
                    callListenerAsync {
                        onInitialText(text ?: "")
                    }

                    isInitialMessage = false
                } else {
                    callListenerAsync {
                        onTextChanged(text ?: "")
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable?, response: Response?) {
                super
                        .onFailure(webSocket, t, response)
                Log
                        .e(TAG, "Websocket error", t)
                callListenerAsync {
                    onError(response?.code(), t)
                }
            }
        }

        webSocket = client
                .newWebSocket(request, listener)
    }

    private fun callListenerAsync(listener: () -> Unit) {
        object : AsyncTask<Void, Void, Void?>() {
            override fun doInBackground(vararg p0: Void?): Void? {
                listener()
                return null
            }
        }
                .execute()
    }

    /**
     * Send a patch to the server
     */
    fun sendPatch(previousText: String, newText: String) {
        val diffMatchPatch = diff_match_patch()

        val diffs = diffMatchPatch
                .diff_main(previousText, newText)
        val patches = diffMatchPatch
                .patch_make(diffs)

        val editRequestModel = jsonObject("documentId" to documentId, "patches" to diffMatchPatch.patch_toText(patches))

        webSocket
                ?.send(editRequestModel.toString())
    }

    /**
     * Disconnect a websocket
     */
    fun disconnect(code: Int, reason: String) {
        webSocket
                ?.close(code, reason)
    }

    /**
     * Shutdown the websocket client (and all websockets)
     */
    fun shutdown() {
        client
                .dispatcher()
                .executorService()
                .shutdown()
    }

    companion object {
        const val TAG = "DocumentSyncManager"
    }

}