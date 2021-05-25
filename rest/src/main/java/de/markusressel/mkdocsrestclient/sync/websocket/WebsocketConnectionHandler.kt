package de.markusressel.mkdocsrestclient.sync.websocket

import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.EchoWebSocketListenerBase
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Simplifies setting up and communicating over a websocket
 */
class WebsocketConnectionHandler(
    /**
     * The URL at which the server is awaiting a websocket connection
     */
    val url: String,
    /**
     * Configuration for HTTP Basic Auth
     */
    val basicAuthConfig: BasicAuthConfig
) {

    /**
     * Indicates if the websocket is connected
     */
    var isConnected = false

    private var listener: WebsocketConnectionListener? = null

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(3, TimeUnit.SECONDS)
        //            .pingInterval(30, TimeUnit.SECONDS)
        .authenticator { route, response ->
            val credential = Credentials
                .basic(basicAuthConfig.username, basicAuthConfig.password)

            response.request.newBuilder()
                .header("Authorization", credential)
                .build()
        }
        .build()

    private var webSocket: WebSocket? = null

    /**
     * Set a listener to react to websocket events
     *
     * @param listener the listener to use or null to disable it
     */
    fun setListener(listener: WebsocketConnectionListener?) {
        this.listener = listener
    }

    /**
     * Connect the websocket.
     * If the socket is already connected this is a no-op.
     */
    fun connect() {
        if (isConnected) {
            Timber.w("Already connected")
            return
        }

        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, object : EchoWebSocketListenerBase() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                onNewConnectionStatus(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                super.onMessage(webSocket, text)
                listener?.onMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                onNewConnectionStatus(false)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
                onNewConnectionStatus(false)

                Timber.e(t, "Websocket error")
                runOnUiThread {
                    listener?.onConnectionChanged(isConnected, response?.code, t)
                }
            }
        })
    }

    /**
     * Notifies listeners of a connection state change
     */
    private fun onNewConnectionStatus(connected: Boolean) {
        if (isConnected != connected) {
            isConnected = connected
            listener?.onConnectionChanged(isConnected)
        }
    }

    /**
     * Send a message over the websocket.
     *
     * @throws IllegalStateException when the websocket is disconnected
     */
    fun send(message: String) {
        if (!isConnected) {
            throw IllegalStateException("There is no active connection!")
        }
        webSocket?.send(message)
    }

    /**
     * Disconnect a websocket
     */
    fun disconnect(code: Int, reason: String) {
        webSocket?.close(code, reason)
        webSocket = null
        isConnected = false
    }

    /**
     * Shutdown the websocket
     */
    fun shutdown() {
        client.dispatcher.executorService.shutdown()
    }

}