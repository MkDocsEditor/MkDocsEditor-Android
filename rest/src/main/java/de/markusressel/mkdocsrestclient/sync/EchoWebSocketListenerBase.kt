package de.markusressel.mkdocsrestclient.sync

import okhttp3.WebSocket
import okhttp3.WebSocketListener

abstract class EchoWebSocketListenerBase : WebSocketListener() {

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(NORMAL_CLOSURE_STATUS, reason)
    }

    companion object {
        private const val NORMAL_CLOSURE_STATUS = 1000
    }
}