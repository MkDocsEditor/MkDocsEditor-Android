package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.moshi
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.ActorInfo
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.ActorMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.DocumentRenamedMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.ErrorMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.HeartbeatMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.HelloMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.InitialContentMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.MessageEnvelope
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.MessageType
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.PresenceLeaveMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.PresenceMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.PresenceSnapshotMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.SelectionMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.SyncRequestMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.UserInfo
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.rebaseSplices
import org.automerge.SyncState
import java.security.SecureRandom
import java.util.Base64
import timber.log.Timber

/**
 * A selection of another user, resolved to UTF-16 positions in the current text
 */
data class RemoteSelection(
    val anchor: Int,
    val head: Int,
) {
    val start: Int get() = minOf(anchor, head)
    val end: Int get() = maxOf(anchor, head)
}

/**
 * Another connection on the same document
 */
data class RemotePeer(
    val clientId: String,
    val username: String,
    val displayName: String?,
    val color: String?,
    val selections: List<RemoteSelection>,
)

/**
 * Client side of the document websocket protocol (version 1, see docs/sync-protocol.md
 * in MkDocsEditor-Backend), without any networking or threading.
 *
 * - The local copy of the document is loaded from [store] on creation and persisted
 *   with [persist], so edits made offline survive restarts and are merged on reconnect.
 *   The local copy is never replaced by a server copy: documents of the same epoch are
 *   merged through the Automerge sync protocol, and unsynced edits of an old epoch are
 *   rebased onto the new one.
 * - Each persisted copy has its own actor id. A fresh copy loaded from the server gets a
 *   new random actor id, because two copies that change concurrently must never share one.
 * - One [SyncState] per connection, never persisted.
 *
 * Not thread safe: call all methods from one thread.
 */
class DocumentSyncSession(
    documentId: String,
    private val store: LocalDocumentStore,
    private val listener: Listener,
    private val clientName: String = "MkDocsEditor-Android",
    private val random: SecureRandom = SecureRandom(),
) {

    interface Listener {
        /**
         * The whole text changed (first load, or a new epoch). There are no splices
         * to move the selection with.
         */
        fun onTextReplaced(text: String) {}

        /**
         * Remote changes were applied to the text.
         *
         * @param splices the changes, relative to the previous text, applied in order
         */
        fun onRemoteChange(text: String, splices: List<TextSplice>) {}

        fun onReadOnlyChanged(readOnly: Boolean) {}

        fun onPeersChanged(peers: List<RemotePeer>) {}

        /**
         * The local copy changed and should be persisted soon (see [persist])
         */
        fun onLocalCopyChanged() {}

        fun onDocumentRenamed(oldDocumentId: String, newDocumentId: String) {}

        /**
         * The document was deleted on the server. The local copy is kept.
         */
        fun onDocumentDeleted() {}

        fun onServerError(code: String, message: String?) {}
    }

    private class LocalCopy(
        val document: AutomergeTextDocument,
        val actorId: String,
        val epoch: String,
        var serverHeads: List<String>,
    )

    private class Peer(
        val presence: PresenceMessage,
        var resolved: List<RemoteSelection>,
    )

    var documentId: String = documentId
        private set

    /** the state this session last loaded from or wrote to the store */
    private var storedState: ByteArray? = null

    private var local: LocalCopy? = store.load(documentId)?.let { stored ->
        storedState = stored.documentState
        LocalCopy(
            document = AutomergeTextDocument.load(stored.documentState, stored.actorId.hexToBytes()),
            actorId = stored.actorId,
            epoch = stored.epoch,
            serverHeads = stored.serverHeads,
        )
    }

    private var send: ((String) -> Unit)? = null

    /** actor id announced in hello when there is no local copy yet */
    private var pendingActorId: String? = null

    /** sync state of the current connection, set once initial-content arrived */
    private var syncState: SyncState? = null

    private var dirty = false

    private val peers = linkedMapOf<String, Peer>()

    private val actors = mutableMapOf<String, ActorInfo>()

    /** the last selection set with [updateLocalSelection] */
    private var localSelection: Pair<Int, Int>? = null

    /**
     * The current text of the local copy, or null if there is none yet
     */
    val text: String?
        get() = local?.document?.text

    /**
     * true when a local copy exists, so the document can be shown and edited offline
     */
    val hasLocalCopy: Boolean
        get() = local != null

    /**
     * true while connected and the initial content was received
     */
    val isSyncing: Boolean
        get() = syncState != null

    var readOnly: Boolean = false
        private set

    /**
     * The authenticated user of the current connection
     */
    var you: UserInfo? = null
        private set

    /**
     * Known actor id bindings (actor id to user), for authorship
     */
    val actorMap: Map<String, ActorInfo>
        get() = actors

    /**
     * Epoch of the local copy, if any
     */
    val epoch: String?
        get() = local?.epoch

    /**
     * Called when the websocket is open. Sends the hello message.
     *
     * @param send sends one text frame
     * @param ticket an optional websocket ticket (when not using Basic auth on the upgrade)
     */
    fun onConnected(send: (String) -> Unit, ticket: String? = null) {
        this.send = send
        syncState = null
        val current = local
        val actorId = current?.actorId ?: newActorId().also { pendingActorId = it }
        sendMessage(
            HelloMessage(
                documentId = documentId,
                actorId = actorId,
                epoch = current?.epoch,
                ticket = ticket,
                client = clientName,
            )
        )
    }

    /**
     * Called when the websocket closed or failed
     */
    fun onDisconnected() {
        send = null
        syncState = null
        if (peers.isNotEmpty()) {
            peers.clear()
            listener.onPeersChanged(emptyList())
        }
    }

    /**
     * Handles one text frame from the server
     */
    fun onMessage(text: String) {
        val type = moshi.adapter(MessageEnvelope::class.java).fromJson(text)?.type ?: return
        when (type) {
            MessageType.INITIAL_CONTENT -> onInitialContent(parse(text))
            MessageType.SYNC_REQUEST -> onSyncRequest(parse(text))
            MessageType.PRESENCE -> onPresence(parse(text))
            MessageType.PRESENCE_SNAPSHOT -> onPresenceSnapshot(parse(text))
            MessageType.PRESENCE_LEAVE -> onPresenceLeave(parse(text))
            MessageType.ACTOR -> parse<ActorMessage>(text).let {
                actors[it.actorId] = ActorInfo(it.actorId, it.username, it.displayName, it.color)
            }

            MessageType.DOCUMENT_RENAMED -> onDocumentRenamed(parse(text))
            MessageType.DOCUMENT_DELETED -> listener.onDocumentDeleted()
            MessageType.ERROR -> parse<ErrorMessage>(text).let { listener.onServerError(it.code, it.message) }
            MessageType.HEARTBEAT -> Unit
            // unknown types must be ignored
            else -> Unit
        }
    }

    /**
     * Applies a new editor text to the local copy as splices. Works offline too.
     * Call [flushSync] afterwards (throttled) to send the changes.
     *
     * @return false if the text could not be applied (no local copy, or read-only)
     */
    fun onLocalTextChanged(newText: String): Boolean {
        val current = local ?: return false
        if (readOnly) {
            return false
        }
        val splices = current.document.applyLocalText(newText)
        if (splices.isNotEmpty()) {
            markDirty()
            resolvePeers()
        }
        return true
    }

    /**
     * Sends sync messages until there is nothing left to send
     */
    fun flushSync() {
        val current = local ?: return
        val state = syncState ?: return
        while (true) {
            val message = current.document.generateSyncMessage(state) ?: break
            sendMessage(
                SyncRequestMessage(
                    documentId = documentId,
                    syncMessage = Base64.getEncoder().encodeToString(message),
                )
            )
        }
        updateServerHeads()
    }

    /**
     * Sets the local selection and sends it to the other users.
     *
     * @param anchor UTF-16 index where the selection started, or null if the editor has no focus
     * @param head UTF-16 index of the caret
     */
    fun updateLocalSelection(anchor: Int?, head: Int?) {
        localSelection = if (anchor != null && head != null) anchor to head else null
        sendPresence()
    }

    /**
     * Sends a heartbeat (the caller decides when, see the protocol: every 5 s of silence)
     */
    fun sendHeartbeat() {
        sendMessage(HeartbeatMessage())
    }

    /**
     * Writes the local copy to the store if it changed since the last call
     */
    fun persist() {
        val current = local ?: return
        if (!dirty) {
            return
        }
        reconcileWithStore(current)
        val state = current.document.save()
        store.save(
            documentId,
            StoredDocument(
                documentState = state,
                actorId = current.actorId,
                epoch = current.epoch,
                serverHeads = current.serverHeads,
            )
        )
        storedState = state
        dirty = false
    }

    /**
     * Never overwrites a copy that someone else wrote under this id since this session read or
     * wrote it (a redirect may have re-keyed a copy of a moved document onto it): a copy of
     * the same lineage is merged into this one, any other copy is kept aside as a backup.
     */
    private fun reconcileWithStore(current: LocalCopy) {
        val stored = store.load(documentId) ?: return
        val known = storedState
        if (known != null && stored.documentState.contentEquals(known)) return
        if (stored.epoch == current.epoch) {
            try {
                val splices = current.document.merge(AutomergeTextDocument.load(stored.documentState))
                if (splices.isNotEmpty()) listener.onRemoteChange(current.document.text, splices)
                return
            } catch (e: Exception) {
                Timber.e(e, "Could not merge the stored copy of $documentId")
            }
        }
        store.keepAside(documentId)
    }

    private fun onInitialContent(message: InitialContentMessage) {
        if (message.documentId != documentId) {
            return
        }
        val current = local
        val bytes = Base64.getDecoder().decode(message.documentState)

        when {
            current != null && current.epoch == message.epoch -> {
                // same lineage: keep the local copy, the sync merges offline edits
            }

            current != null -> {
                // the server started a new lineage: load it with a new actor id and
                // re-apply the edits the server never received
                val actorId = newActorId()
                val document = AutomergeTextDocument.load(bytes, actorId.hexToBytes())
                val serverHeads = document.headsHex
                val unsynced = rebaseSplices(
                    base = current.document.textAt(current.serverHeads),
                    local = current.document.text,
                    server = document.text,
                )
                document.applyLocalSplices(unsynced)
                local = LocalCopy(document, actorId, message.epoch, serverHeads)
                markDirty()
                listener.onTextReplaced(document.text)
            }

            else -> {
                val actorId = pendingActorId ?: newActorId()
                val document = AutomergeTextDocument.load(bytes, actorId.hexToBytes())
                local = LocalCopy(document, actorId, message.epoch, document.headsHex)
                markDirty()
                listener.onTextReplaced(document.text)
            }
        }
        pendingActorId = null

        you = message.you
        message.actors.forEach { actors[it.actorId] = it }
        if (readOnly != message.readOnly) {
            readOnly = message.readOnly
            listener.onReadOnlyChanged(readOnly)
        }

        syncState = SyncState()
        flushSync()
        sendPresence()
    }

    private fun onSyncRequest(message: SyncRequestMessage) {
        if (message.documentId != documentId) {
            return
        }
        val current = local ?: return
        val state = syncState ?: return
        val splices = current.document.receiveSyncMessage(state, Base64.getDecoder().decode(message.syncMessage))
        if (splices.isNotEmpty()) {
            markDirty()
            listener.onRemoteChange(current.document.text, splices)
            resolvePeers()
        }
        flushSync()
    }

    private fun updateServerHeads() {
        val current = local ?: return
        val state = syncState ?: return
        if (state.isInSync(current.document.document)) {
            val heads = current.document.headsHex
            if (heads != current.serverHeads) {
                current.serverHeads = heads
                markDirty()
            }
        }
    }

    private fun onPresence(message: PresenceMessage) {
        val clientId = message.clientId ?: return
        peers[clientId] = Peer(message, resolve(message, previous = peers[clientId]?.resolved))
        notifyPeers()
    }

    private fun onPresenceSnapshot(message: PresenceSnapshotMessage) {
        peers.clear()
        message.peers.forEach { presence ->
            val clientId = presence.clientId ?: return@forEach
            peers[clientId] = Peer(presence, resolve(presence, previous = null))
        }
        notifyPeers()
    }

    private fun onPresenceLeave(message: PresenceLeaveMessage) {
        if (peers.remove(message.clientId) != null) {
            notifyPeers()
        }
    }

    private fun onDocumentRenamed(message: DocumentRenamedMessage) {
        val oldId = documentId
        if (message.documentId != oldId) {
            return
        }
        val newId = message.newDocumentId
        // write the copy with all local edits under the old id, then move it in one step
        local?.let { dirty = true }
        persist()
        store.move(oldId, newId)
        documentId = newId
        // the store may have merged an older copy of the new id into it
        val current = local
        store.load(newId)?.let { stored ->
            storedState = stored.documentState
            if (current == null || !stored.documentState.contentEquals(current.document.save())) {
                local = LocalCopy(
                    document = AutomergeTextDocument.load(stored.documentState, stored.actorId.hexToBytes()),
                    actorId = stored.actorId,
                    epoch = stored.epoch,
                    serverHeads = stored.serverHeads,
                )
                val text = local!!.document.text
                if (current == null || text != current.document.text) listener.onTextReplaced(text)
            }
        }
        listener.onDocumentRenamed(oldId, newId)
    }

    /**
     * Resolves the cursors of a peer. A cursor that cannot be resolved yet (its change
     * has not arrived) keeps its previous position.
     */
    private fun resolve(presence: PresenceMessage, previous: List<RemoteSelection>?): List<RemoteSelection> {
        val document = local?.document ?: return emptyList()
        return presence.selections.mapIndexedNotNull { index, selection ->
            val old = previous?.getOrNull(index)
            val anchor = document.lookupCursor(selection.anchor) ?: old?.anchor
            val head = document.lookupCursor(selection.head) ?: old?.head
            if (anchor != null && head != null) RemoteSelection(anchor, head) else null
        }
    }

    private fun resolvePeers() {
        if (peers.isEmpty()) {
            return
        }
        peers.values.forEach { it.resolved = resolve(it.presence, it.resolved) }
        notifyPeers()
    }

    private fun notifyPeers() {
        listener.onPeersChanged(
            peers.values.map { peer ->
                RemotePeer(
                    clientId = peer.presence.clientId.orEmpty(),
                    username = peer.presence.username.orEmpty(),
                    displayName = peer.presence.displayName,
                    color = peer.presence.color,
                    selections = peer.resolved,
                )
            }
        )
    }

    private fun sendPresence() {
        val document = local?.document ?: return
        if (syncState == null) {
            return
        }
        val selections = localSelection?.let { (anchor, head) ->
            listOf(SelectionMessage(anchor = document.makeCursor(anchor), head = document.makeCursor(head)))
        } ?: emptyList()
        sendMessage(PresenceMessage(documentId = documentId, selections = selections))
    }

    private fun markDirty() {
        dirty = true
        listener.onLocalCopyChanged()
    }

    private fun newActorId(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.toHex()
    }

    private inline fun <reified T : Any> sendMessage(message: T) {
        send?.invoke(moshi.adapter(T::class.java).toJson(message))
    }

    private inline fun <reified T : Any> parse(text: String): T {
        return requireNotNull(moshi.adapter(T::class.java).fromJson(text))
    }
}
