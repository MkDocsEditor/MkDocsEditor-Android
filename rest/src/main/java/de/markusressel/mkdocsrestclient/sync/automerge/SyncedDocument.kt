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

    /** the id of this client instance on the server, it can differ from the one this client asked for */
    val instanceIdInUse: String?

    /** Connects (and keeps reconnecting) until [disconnect] is called */
    fun connect()

    fun disconnect(reason: String)

    fun onLocalSelectionChanged(anchor: Int?, head: Int?)

    /** @param top the first visible character, null if the editor is not visible */
    fun onLocalViewportChanged(top: Int?, bottom: Int?)

    /** @param color what other users see this client in, `#rrggbb`, null for the color of the account */
    fun onLocalColorChanged(color: String?)

    /** Writes the local copy to disk right away */
    fun persistNow()

    /** Ends the connection and writes the local copy. The object can not be used after this. */
    fun shutdown()
}
