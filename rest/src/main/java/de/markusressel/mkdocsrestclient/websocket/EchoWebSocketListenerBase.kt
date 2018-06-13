package de.markusressel.mkdocsrestclient.websocket

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

abstract class EchoWebSocketListenerBase : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response?) {
    }

    override fun onMessage(webSocket: WebSocket, text: String?) {
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString?) {
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String?) {
        webSocket.close(NORMAL_CLOSURE_STATUS, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable?, response: Response?) {
    }

    companion object {
        private val NORMAL_CLOSURE_STATUS = 1000
    }
}