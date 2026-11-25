package de.markusressel.mkdocsrestclient.sync.websocket

import de.markusressel.mkdocsrestclient.AuthorizationProvider
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
     * Supplies the Authorization header of the upgrade request (a bearer session), evaluated
     * on every connect. Without one the server waits for a ticket in the hello message.
     */
    authorizationProvider: AuthorizationProvider,
    client: OkHttpClient = defaultClient,
) {

    /**
     * Reads the token in an interceptor, because [connect] is called on the main thread and the
     * token can take a while: it may have to be decrypted, and others may hold the lock of the
     * store. So connecting stays synchronous, and its order with [disconnect] does not change.
     */
    private val client = client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val authorization = authorizationProvider.authorization()
            chain.proceed(
                if (authorization == null) request
                else request.newBuilder().header("Authorization", authorization).build()
            )
        }
        .build()

    private var listener: WebsocketConnectionListener? = null

    @Volatile
    private var webSocket: WebSocket? = null

    /**
     * The listener of the current socket. Callbacks check it rather than [webSocket]: OkHttp can call one before
     * `newWebSocket` returns, a failure even at once, and dropping that one left the connection stuck.
     */
    @Volatile
    private var current: WebSocketListener? = null

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

        val request = Request.Builder().url(url).build()

        val socketListener = object : WebSocketListener() {
            private val isCurrent get() = current === this

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!isCurrent) return
                isConnected = true
                listener?.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (!isCurrent) return
                listener?.onMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!markClosed(this)) return
                listener?.onClosed(code)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!markClosed(this)) return
                Timber.e(t, "Websocket error")
                listener?.onClosed(code = null, httpStatus = response?.code, throwable = t)
            }
        }
        current = socketListener
        val socket = client.newWebSocket(request, socketListener)
        // it may have failed already
        if (current === socketListener) webSocket = socket
    }

    /** @return false if the socket of [socketListener] is not the current one anymore */
    @Synchronized
    private fun markClosed(socketListener: WebSocketListener): Boolean {
        if (current !== socketListener) return false
        current = null
        isConnected = false
        webSocket = null
        return true
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
        current = null
        isConnected = false
    }

    companion object {
        private const val UPGRADE_TIMEOUT_MS = 15_000L

        private val defaultClient: OkHttpClient by lazy { newDefaultClient() }

        /**
         * An open socket can be quiet for a long time, so it has no read timeout. That would also let an upgrade that
         * connects but never gets an answer wait forever. The call timeout only covers the upgrade: OkHttp ends it once
         * the socket is open.
         */
        internal fun newDefaultClient(upgradeTimeoutMs: Long = UPGRADE_TIMEOUT_MS): OkHttpClient =
            OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(5, TimeUnit.SECONDS)
                .callTimeout(upgradeTimeoutMs, TimeUnit.MILLISECONDS)
                .build()
    }
}
