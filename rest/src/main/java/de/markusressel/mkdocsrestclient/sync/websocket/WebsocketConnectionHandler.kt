package de.markusressel.mkdocsrestclient.sync.websocket

import de.markusressel.mkdocsrestclient.BasicAuthConfig
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * Simplifies setting up and communicating over a websocket.
 *
 * Listener callbacks run on an OkHttp thread. Callbacks of a socket that was replaced
 * by a newer [connect] or closed with [disconnect] are dropped.
 */
class WebsocketConnectionHandler(
    /**
     * Configuration for HTTP Basic Auth. The header is sent with the upgrade request
     * right away: the server does not answer with 401, it waits for a ticket instead.
     */
    private val basicAuthConfig: BasicAuthConfig?,
    private val client: OkHttpClient = defaultClient,
) {

    private var listener: WebsocketConnectionListener? = null

    @Volatile
    private var webSocket: WebSocket? = null

    /**
     * Indicates if the websocket is connected
     */
    @Volatile
    var isConnected = false
        private set

    fun setListener(listener: WebsocketConnectionListener?) {
        this.listener = listener
    }

    /**
     * Connects to the given URL, replacing any previous connection
     */
    @Synchronized
    fun connect(url: String) {
        webSocket?.close(1000, "Reconnecting")
        isConnected = false

        val request = Request.Builder().url(url).apply {
            basicAuthConfig?.let {
                header("Authorization", Credentials.basic(it.username, it.password))
            }
        }.build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            private fun isCurrent(socket: WebSocket) = socket === webSocket

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent(webSocket)) return
                isConnected = true
                listener?.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent(webSocket)) return
                listener?.onMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!isCurrent(webSocket)) return
                markClosed()
                listener?.onClosed(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!isCurrent(webSocket)) return
                Timber.e(t, "Websocket error")
                markClosed()
                listener?.onClosed(code = null, httpStatus = response?.code, throwable = t)
            }
        })
    }

    @Synchronized
    private fun markClosed() {
        isConnected = false
        webSocket = null
    }

    /**
     * Send a message over the websocket.
     *
     * @return false if there is no open connection
     */
    fun send(message: String): Boolean {
        val socket = webSocket
        if (!isConnected || socket == null) {
            return false
        }
        return socket.send(message)
    }

    /**
     * Closes the connection. The listener is not called.
     */
    @Synchronized
    fun disconnect(code: Int, reason: String) {
        webSocket?.close(code, reason)
        webSocket = null
        isConnected = false
    }

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
