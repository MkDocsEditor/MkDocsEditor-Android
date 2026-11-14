package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.moshi
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.ActorInfo
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.AgentMessage
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
import java.util.concurrent.TimeUnit
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.LastPositionMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.LocalPresenceMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.ViewportMessage
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
    /** the stable id of the account, usernames can change */
    val accountId: String? = null,
    /** when this connection last sent its presence, as an ISO instant in UTC */
    val lastActiveAt: String? = null,
    val viewport: RemoteViewport? = null,
    /** the client instance of this connection. A server without instances has none, then each connection is one. */
    val instanceId: String = clientId,
    val deviceName: String? = null,
    /** drawn instead of [color], which stays the color of the account */
    val instanceColor: String? = null,
    /** version of the profile picture of [accountId], null without one */
    val avatar: String? = null,
    /** set when this is an AI agent of [accountId], which has no cursor and no viewport */
    val agent: RemoteAgent? = null,
)

/** An AI agent that works with an app token of an account */
data class RemoteAgent(
    val tokenId: String,
    /** the name of the app token */
    val name: String,
)

/** The text another user is looking at, as UTF-16 indices of the first and the last visible character */
data class RemoteViewport(
    val top: Int,
    val bottom: Int,
)

/** Where an account last was in this document */
data class RemotePosition(
    val selection: RemoteSelection?,
    val viewport: RemoteViewport?,
)

/**
 * The cursor of a client instance that has left, which stays for a while
 *
 * @param remainingMs how long it stays from now on
 */
data class RemoteGhost(
    val instanceId: String,
    val accountId: String,
    val username: String,
    val displayName: String?,
    val color: String?,
    val selections: List<RemoteSelection>,
    val viewport: RemoteViewport?,
    val remainingMs: Long,
    val deviceName: String? = null,
    val instanceColor: String? = null,
    val avatar: String? = null,
    val agent: RemoteAgent? = null,
)

private fun AgentMessage.toRemote() = RemoteAgent(tokenId, name)

/** A new id of a client instance, which the server expects as 32 lowercase hex characters */
fun newInstanceId(random: SecureRandom = SecureRandom()): String =
    ByteArray(16).also { random.nextBytes(it) }.toHex()

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
    /** the server this session talks to, stored with the copy */
    private val origin: String? = null,
    private val now: () -> Long = { TimeUnit.NANOSECONDS.toMillis(System.nanoTime()) },
    /** see [newInstanceId] */
    private val instanceId: String? = null,
) : EditableTextDocument {

    interface Listener {
        /** @param colors the color of each known user by username, also of those who are not online and of this user */
        fun onAuthorColorsChanged(colors: Map<String, String?>) {}

        /**
         * The whole text changed (first load, or a new epoch). There are no splices
         * to move the selection with.
         */
        fun onTextReplaced(text: String) {}

        /**
         * Remote changes were applied to the text.
         *
         * @param splices the changes, relative to the previous text, applied in order
         * @param author the username of whoever made all of them, null if that is not known or they come from several
         * users
         */
        fun onRemoteChange(text: String, splices: List<TextSplice>, author: String?) {}

        fun onReadOnlyChanged(readOnly: Boolean) {}

        fun onPeersChanged(peers: List<RemotePeer>) {}

        /** @param positions where accounts last were in this document, by account */
        fun onLastPositionsChanged(positions: Map<String, RemotePosition>) {}

        /** the ghosts changed. Their time runs out without another call, see [RemoteGhost.remainingMs] */
        fun onGhostsChanged(ghosts: List<RemoteGhost>) {}

        /** @param known whether the server told us who is here, it did not while disconnected or still connecting */
        fun onPresenceKnownChanged(known: Boolean) {}

        /**
         * The local copy changed and should be persisted soon (see [persist])
         */
        fun onLocalCopyChanged() {}

        /**
         * See [hasUnsyncedChanges]
         */
        fun onUnsyncedChangesChanged(unsynced: Boolean) {}

        fun onDocumentRenamed(oldDocumentId: String, newDocumentId: String) {}

        /**
         * The document was deleted on the server. The local copy is kept.
         */
        fun onDocumentDeleted() {}

        fun onServerError(code: String, message: String?) {}

        /** The server answered for this document: it sent the document or a sync message */
        fun onServerAnswered() {}
    }

    private class LocalCopy(
        val document: AutomergeTextDocument,
        val actorId: String,
        val epoch: String,
        var serverHeads: List<String>,
    )

    private class Ghost(
        val instanceId: String,
        val accountId: String,
        val username: String,
        val displayName: String?,
        val color: String?,
        val selections: List<SelectionMessage>,
        val viewport: ViewportMessage?,
        val expiresAt: Long,
        val deviceName: String?,
        val instanceColor: String?,
        val avatar: String?,
        val agent: RemoteAgent?,
    )

    private class Peer(
        val presence: PresenceMessage,
        var resolved: List<RemoteSelection>,
    )

    var documentId: String = documentId
        private set

    /** the state this session last loaded from or wrote to the store */
    private var storedState: ByteArray? = null

    /** the server of the copy, when it was written by a session that knows it */
    private var storedOrigin: String? = null

    // Ids come from the path, so two servers can share one. A copy of another server stays in the store for that server
    // until this session persists, which keeps it aside (see reconcileWithStore). Its edits must never reach this server.
    private var local: LocalCopy? = store.load(documentId)?.takeUnless { it.isFromOtherServer(origin) }?.let { stored ->
        storedState = stored.documentState
        storedOrigin = stored.origin
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
    private var lastPositions = emptyList<LastPositionMessage>()
    private val ghosts = linkedMapOf<String, Ghost>()

    /** the last visible text set with [updateLocalViewport] */
    private var localViewport: Pair<Int, Int>? = null

    /** the last color set with [updateLocalColor] */
    private var localColor: String? = null

    private val actors = mutableMapOf<String, ActorInfo>()

    /** the last selection set with [updateLocalSelection] */
    private var localSelection: Pair<Int, Int>? = null

    /**
     * The current text of the local copy, or null if there is none yet
     */
    override val text: String?
        get() = local?.document?.text

    override val version: DocumentVersion?
        get() = local?.document?.version

    override val localAuthor: String?
        get() = you?.username

    /**
     * true when a local copy exists, so the document can be shown and edited offline
     */
    val hasLocalCopy: Boolean
        get() = local != null

    /**
     * true when the local copy holds changes the server has not confirmed yet
     */
    val hasUnsyncedChanges: Boolean
        get() = local?.let { it.document.headsHex.toSet() != it.serverHeads.toSet() } ?: false

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
                instanceId = instanceId,
                instanceColor = localColor,
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
        if (ghosts.isNotEmpty()) {
            ghosts.clear()
            notifyGhosts()
        }
        listener.onPresenceKnownChanged(false)
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
                actors[it.actorId] = ActorInfo(it.actorId, it.username, it.displayName, it.color, it.agent)
                reportAuthorColors()
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
     * Applies an edit of the editor to the local copy. Works offline too.
     * Call [flushSync] afterwards (throttled) to send the changes.
     *
     * @return see [EditableTextDocument.applyLocalEdit], null when there is no local copy or
     * the document is read-only
     */
    override fun applyLocalEdit(
        base: DocumentVersion,
        baseText: String,
        splices: List<TextSplice>,
    ): DocumentVersion? {
        val current = local ?: return null
        if (readOnly) {
            return null
        }
        val applied = current.document.applySplicesAt(base, baseText, splices)
        if (splices.isNotEmpty()) {
            markDirty()
            resolvePeers()
        }
        return applied
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
    fun updateLocalSelection(anchor: Int?, head: Int?) = updateLocalPresence(anchor to head, null)

    /**
     * Sets the cursor and the viewport at once, so that they go out in one message
     *
     * @param selection anchor and head, see [updateLocalSelection], null to keep the last one
     * @param viewport top and bottom, see [updateLocalViewport], null to keep the last one
     */
    fun updateLocalPresence(selection: Pair<Int?, Int?>?, viewport: Pair<Int?, Int?>?) {
        selection?.let { (anchor, head) -> localSelection = if (anchor != null && head != null) anchor to head else null }
        viewport?.let { (top, bottom) -> localViewport = if (top != null && bottom != null) top to bottom else null }
        sendPresence()
    }

    /**
     * Sets the text the user is looking at and sends it to the other users.
     *
     * @param top UTF-16 index of the first visible character, or null if the editor is not visible
     * @param bottom UTF-16 index of the last visible character
     */
    fun updateLocalViewport(top: Int?, bottom: Int?) = updateLocalPresence(null, top to bottom)

    /** the id other clients know this client by, null if there is none yet */
    val instanceIdInUse: String?
        get() = you?.instanceId ?: instanceId

    /**
     * Sets the color the other users see this client in, without changing the color of the account
     *
     * @param color `#rrggbb`, or null for the color of the account
     */
    fun updateLocalColor(color: String?) {
        localColor = color
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
                heads = current.document.headsHex,
                origin = origin ?: storedOrigin,
            )
        )
        storedState = state
        dirty = false
        reportUnsynced()
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
                if (splices.isNotEmpty()) listener.onRemoteChange(current.document.text, splices, author = null)
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
        reportAuthorColors()
        if (readOnly != message.readOnly) {
            readOnly = message.readOnly
            listener.onReadOnlyChanged(readOnly)
        }

        syncState = SyncState()
        flushSync()
        sendPresence()
        listener.onServerAnswered()
    }

    /**
     * The server binds each actor id to one user and refuses changes of an actor that belongs to someone else, so the
     * actor of a change names its author for sure.
     *
     * @return the user who made all changes of [actorIds], null if unknown, imported, or from several users
     */
    private fun authorOf(actorIds: Set<String>?): String? {
        val usernames = actorIds?.map { actors[it]?.username ?: return null }?.toSet() ?: return null
        // ":disk" and ":server" are the backend itself
        return usernames.singleOrNull()?.takeUnless { it.startsWith(":") || it.isBlank() }
    }

    private fun reportAuthorColors() {
        val colors = actors.values.associate { it.username to it.color }
        listener.onAuthorColorsChanged(colors + listOfNotNull(you).associate { it.username to it.color })
    }

    private fun onSyncRequest(message: SyncRequestMessage) {
        if (message.documentId != documentId) {
            return
        }
        val current = local ?: return
        val state = syncState ?: return
        val before = current.document.heads
        val splices = current.document.receiveSyncMessage(state, Base64.getDecoder().decode(message.syncMessage))
        if (splices.isNotEmpty()) {
            markDirty()
            listener.onRemoteChange(current.document.text, splices, authorOf(current.document.actorsSince(before)))
            resolvePeers()
        }
        flushSync()
        listener.onServerAnswered()
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
        // it is back
        if (ghosts.remove(message.instanceId ?: clientId) != null) notifyGhosts()
    }

    private fun onPresenceSnapshot(message: PresenceSnapshotMessage) {
        lastPositions = message.lastPositions
        notifyLastPositions()
        peers.clear()
        message.peers.forEach { presence ->
            val clientId = presence.clientId ?: return@forEach
            peers[clientId] = Peer(presence, resolve(presence, previous = null))
        }
        notifyPeers()
        ghosts.clear()
        message.ghosts.forEach { ghost ->
            ghosts[ghost.instanceId] = Ghost(
                instanceId = ghost.instanceId,
                accountId = ghost.accountId,
                username = ghost.username.orEmpty(),
                displayName = ghost.displayName,
                color = ghost.color,
                selections = ghost.selections,
                viewport = ghost.viewport,
                expiresAt = now() + ghost.ghostMs,
                deviceName = ghost.deviceName,
                instanceColor = ghost.instanceColor,
                avatar = ghost.avatar,
                agent = ghost.agent?.toRemote(),
            )
        }
        notifyGhosts()
        listener.onPresenceKnownChanged(true)
    }

    private fun onPresenceLeave(message: PresenceLeaveMessage) {
        val left = peers.remove(message.clientId)
        if (left != null) {
            notifyPeers()
        }
        val accountId = message.accountId
        val ghostMs = message.ghostMs
        if (accountId != null && ghostMs != null) {
            val instanceId = message.instanceId ?: left?.presence?.instanceId ?: message.clientId
            ghosts[instanceId] = Ghost(
                instanceId = instanceId,
                accountId = accountId,
                username = left?.presence?.username.orEmpty(),
                displayName = left?.presence?.displayName,
                color = left?.presence?.color,
                selections = message.selections,
                viewport = message.viewport,
                expiresAt = now() + ghostMs,
                deviceName = message.deviceName,
                instanceColor = message.instanceColor,
                avatar = message.avatar ?: left?.presence?.avatar,
                agent = left?.presence?.agent?.toRemote(),
            )
            notifyGhosts()
        }
    }

    private fun notifyGhosts() {
        val time = now()
        ghosts.values.removeAll { it.expiresAt <= time }
        listener.onGhostsChanged(
            ghosts.values.map { ghost ->
                RemoteGhost(
                    instanceId = ghost.instanceId,
                    accountId = ghost.accountId,
                    username = ghost.username,
                    displayName = ghost.displayName,
                    color = ghost.color,
                    selections = resolve(ghost.selections, previous = null),
                    viewport = resolve(ghost.viewport),
                    remainingMs = ghost.expiresAt - time,
                    deviceName = ghost.deviceName,
                    instanceColor = ghost.instanceColor,
                    avatar = ghost.avatar,
                    agent = ghost.agent,
                )
            }
        )
    }

    private fun resolve(viewport: ViewportMessage?): RemoteViewport? {
        val document = local?.document ?: return null
        val top = viewport?.let { document.lookupCursor(it.top) } ?: return null
        val bottom = document.lookupCursor(viewport.bottom) ?: return null
        return RemoteViewport(top, bottom)
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
                reportUnsynced()
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
    private fun resolve(presence: PresenceMessage, previous: List<RemoteSelection>?): List<RemoteSelection> =
        resolve(presence.selections, previous)

    private fun resolve(selections: List<SelectionMessage>, previous: List<RemoteSelection>?): List<RemoteSelection> {
        val document = local?.document ?: return emptyList()
        return selections.mapIndexedNotNull { index, selection ->
            val old = previous?.getOrNull(index)
            val anchor = document.lookupCursor(selection.anchor) ?: old?.anchor
            val head = document.lookupCursor(selection.head) ?: old?.head
            if (anchor != null && head != null) RemoteSelection(anchor, head) else null
        }
    }

    /** A cursor that cannot be resolved is left out: its text may be deleted by now. */
    private fun notifyLastPositions() {
        if (local == null || lastPositions.isEmpty()) return
        listener.onLastPositionsChanged(
            lastPositions.mapNotNull { position ->
                val selection = resolve(position.selections.take(1), previous = null).firstOrNull()
                val viewport = resolve(position.viewport)
                if (selection == null && viewport == null) null else position.accountId to RemotePosition(selection, viewport)
            }.toMap()
        )
    }

    private fun resolvePeers() {
        notifyLastPositions()
        if (ghosts.isNotEmpty()) notifyGhosts()
        if (peers.isEmpty()) {
            return
        }
        peers.values.forEach { it.resolved = resolve(it.presence, it.resolved) }
        notifyPeers()
    }

    private fun notifyPeers() {
        val own = instanceIdInUse
        listener.onPeersChanged(
            // an old connection of this client can still be open on a half-open socket
            peers.values.filter { it.presence.instanceId == null || it.presence.instanceId != own }.map { peer ->
                RemotePeer(
                    clientId = peer.presence.clientId.orEmpty(),
                    username = peer.presence.username.orEmpty(),
                    displayName = peer.presence.displayName,
                    color = peer.presence.color,
                    selections = peer.resolved,
                    accountId = peer.presence.accountId,
                    lastActiveAt = peer.presence.lastActiveAt,
                    viewport = resolve(peer.presence.viewport),
                    instanceId = peer.presence.instanceId ?: peer.presence.clientId.orEmpty(),
                    deviceName = peer.presence.deviceName,
                    instanceColor = peer.presence.instanceColor,
                    avatar = peer.presence.avatar,
                    agent = peer.presence.agent?.toRemote(),
                )
            }
        )
    }

    private fun sendPresence() {
        val document = local?.document ?: return
        if (syncState == null) {
            return
        }
        // a cursor runs on a timer, outside of any caller that could catch a failure, so one must not end the app
        val (selections, viewport) = try {
            val selections = localSelection?.let { (anchor, head) ->
                listOf(SelectionMessage(anchor = document.makeCursor(anchor), head = document.makeCursor(head)))
            } ?: emptyList()
            val viewport = localViewport?.let { (top, bottom) ->
                ViewportMessage(top = document.makeCursor(top), bottom = document.makeCursor(bottom))
            }
            selections to viewport
        } catch (e: Exception) {
            Timber.e(e, "Could not create the cursors of the local presence")
            return
        }
        val message = LocalPresenceMessage(documentId = documentId, selections = selections, viewport = viewport, instanceColor = localColor)
        send?.invoke(moshi.adapter(LocalPresenceMessage::class.java).serializeNulls().toJson(message))
    }

    private fun markDirty() {
        dirty = true
        listener.onLocalCopyChanged()
        reportUnsynced()
    }

    private var reportedUnsynced = hasUnsyncedChanges

    private fun reportUnsynced() {
        val unsynced = hasUnsyncedChanges
        if (unsynced != reportedUnsynced) {
            reportedUnsynced = unsynced
            listener.onUnsyncedChangesChanged(unsynced)
        }
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
