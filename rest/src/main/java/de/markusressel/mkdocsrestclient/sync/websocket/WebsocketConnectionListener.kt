package de.markusressel.mkdocsrestclient.sync.websocket

interface WebsocketConnectionListener {

    /**
     * Called when the websocket is open
     */
    fun onOpen()

    /**
     * Called when a new message was received from the websocket connection.
     *
     * @param text the message received
     */
    fun onMessage(text: String)

    /**
     * Called when the websocket was closed by the server or failed.
     * Not called for [WebsocketConnectionHandler.disconnect].
     *
     * @param code the websocket close code, or null if the connection failed
     * @param httpStatus the HTTP status of a failed upgrade, if any
     * @param throwable the error of a failed connection, if any
     */
    fun onClosed(code: Int?, httpStatus: Int? = null, throwable: Throwable? = null)
}
