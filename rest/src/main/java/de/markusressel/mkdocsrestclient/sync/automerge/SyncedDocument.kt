package de.markusressel.mkdocsrestclient.sync.automerge

/**
 * A document that is kept in sync with the server, see [AutomergeDocumentSyncManager]
 */
interface SyncedDocument : EditableTextDocument {
    val documentId: String
    val hasLocalCopy: Boolean
    val hasUnsyncedChanges: Boolean
    val readOnly: Boolean
    val isConnected: Boolean

    /** Connects (and keeps reconnecting) until [disconnect] is called */
    fun connect()

    fun disconnect(reason: String)

    fun onLocalSelectionChanged(anchor: Int?, head: Int?)

    /** Writes the local copy to disk right away */
    fun persistNow()

    /** Ends the connection and writes the local copy. The object can not be used after this. */
    fun shutdown()
}
