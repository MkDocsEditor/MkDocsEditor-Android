package de.markusressel.mkdocsrestclient.sync.websocket

interface WebsocketConnectionListener {

    /**
     * Called when the connection status of the websocket changes
     *
     * @param connected true, when the websocket is now connected, false otherwise
     * @param errorCode optional error code if the connection was ended
     * @param throwable optional exception that occurred
     */
    fun onConnectionChanged(connected: Boolean, errorCode: Int? = null, throwable: Throwable? = null)

    /**
     * Called when a new message was received from the websocket connection.
     *
     * @text the message received
     */
    fun onMessage(text: String)

}