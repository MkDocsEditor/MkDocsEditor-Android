package de.markusressel.mkdocseditor.feature.editor.ui

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.SyncedDocument
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** how long opening a document waits for the redirect lookups of the local copies */
private const val REDIRECT_TIMEOUT_MS = 5_000L

/** what the database knows about a document */
internal data class CachedDocument(val name: String, val text: String?)

/**
 * The answer when a document is loaded
 *
 * @param document what is known about the document
 * @param error why the server could not be asked
 */
internal data class DocumentLoad(val document: CachedDocument?, val error: Throwable? = null)

internal data class ConnectionStatus(
    val connected: Boolean,
    val errorCode: Int? = null,
    val throwable: Throwable? = null
) {
    /**
     * the server refused the session: HTTP 401 on the upgrade, or close code 4001
     */
    val isAuthenticationFailure: Boolean
        get() = !connected && (errorCode == 401 || errorCode == CloseCode.UNAUTHORIZED)
}

/**
 * "Offline" means the offline mode of the settings, or that the server does not answer: the
 * document could not be loaded, and the connection is not back yet. A server that answers and
 * refuses the session is not offline, the user has to sign in again.
 */
internal fun isOfflineBannerVisible(
    offlineModeEnabled: Boolean,
    documentLoadFailed: Boolean,
    connectionStatus: ConnectionStatus?,
): Boolean {
    if (offlineModeEnabled) return true
    if (!documentLoadFailed || connectionStatus?.connected == true) return false
    return connectionStatus?.isAuthenticationFailure != true
}

/**
 * What a [DocumentSession] needs from the rest of the app
 */
internal interface DocumentSessionEnvironment {
    val ioDispatcher: CoroutineDispatcher

    val offlineMode: StateFlow<Boolean>

    /** whether the editor connects on its own when the offline mode ends */
    val connectsAutomatically: Boolean

    /** a backend id, whenever its session changes */
    val sessionChanges: Flow<Long>

    fun isSignedIn(backendId: Long): Boolean

    /** The server refused the session of the backend */
    fun onUnauthenticated(backendId: Long)

    suspend fun currentBackend(): BackendConfig?

    /** Reads and parses the local copy, so this is not called on the main thread */
    fun createSyncedDocument(
        documentId: String,
        serverConfig: BackendServerConfig,
        backendId: Long,
        listener: AutomergeDocumentSyncManager.Listener,
    ): SyncedDocument

    fun hasLocalCopy(documentId: String): Boolean

    /** @return the current id of a document that may have moved */
    suspend fun followRedirect(documentId: String): String

    suspend fun followAllRedirects()

    /**
     * @param onCached called with what the database knows, before the server is asked
     */
    suspend fun loadDocument(documentId: String, onCached: (CachedDocument?) -> Unit): DocumentLoad

    /** keeps the old offline cache up to date */
    suspend fun updateContentCache(documentId: String, text: String)
}

/**
 * The open document: its connection to the server, its local copy, and whether it can be edited.
 * All calls must come from the main thread, and [scope] must run on it.
 */
internal class DocumentSession(
    private val scope: CoroutineScope,
    private val text: EditorTextController,
    private val environment: DocumentSessionEnvironment,
    private val host: Host,
) {
    /** what the session can not decide on its own */
    interface Host {
        suspend fun loadWikiTree(documentId: String)

        /** The backend of the document is gone, Android restored the editor without it */
        suspend fun onBackendGone()

        /** The offline version is about to be shown, which can not be edited */
        fun onOfflineVersionShown()

        fun onReadOnly()

        fun onRemoteChange()
    }

    data class State(
        val documentId: String? = null,
        val title: String = "",
        val loading: Boolean = false,
        val offlineBannerVisible: Boolean = false,
        val deletedOnServer: Boolean = false,
        /** there is neither a connection nor a text to show */
        val noOfflineVersion: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state = _state.asStateFlow()

    private val _documentId = MutableStateFlow<String?>(null)
    val documentId = _documentId.asStateFlow()

    private val _connectionStatus = MutableStateFlow<ConnectionStatus?>(null)
    val connectionStatus = _connectionStatus.asStateFlow()

    /**
     * Indicates whether a local Automerge copy of the document exists (and may be edited)
     */
    private val localCopyAvailable = MutableStateFlow(false)

    private val readOnly = MutableStateFlow(false)

    /**
     * Indicates whether the edit mode can be activated or not.
     * Editing works offline as soon as a local copy exists; the edits are synced later.
     */
    val editable: StateFlow<Boolean> = combine(
        localCopyAvailable,
        readOnly,
    ) { localCopyAvailable, readOnly ->
        localCopyAvailable && !readOnly
    }.stateIn(scope, SharingStarted.Eagerly, initialValue = false)

    private var syncedDocument: SyncedDocument? = null

    /** the backend of the open document */
    var backendId: Long? = null
        private set

    /** the document could not be loaded from the server, until the server is back */
    private var documentLoadFailed = false

    /** what the database knew when the document was last loaded */
    private var loadedDocument: CachedDocument? = null

    fun start() {
        scope.launch {
            _documentId.collect { documentId -> open(documentId) }
        }

        scope.launch {
            // sync again as soon as the user signed in again
            environment.sessionChanges.collect { backendId ->
                if (backendId == this@DocumentSession.backendId && environment.isSignedIn(backendId) &&
                    syncedDocument?.isConnected == false &&
                    environment.offlineMode.value.not()
                ) {
                    reconnect()
                }
            }
        }

        scope.launch {
            environment.offlineMode.collect { enabled ->
                updateOfflineBanner()

                when {
                    enabled -> disconnect("Offline mode activated")
                    environment.connectsAutomatically -> reconnect()
                    else -> _connectionStatus.value = ConnectionStatus(
                        connected = _connectionStatus.value?.connected ?: false
                    )
                }
            }
        }
    }

    /** Sets the document of this session */
    fun setDocument(documentId: String?) {
        _documentId.value = documentId
    }

    private suspend fun open(documentId: String?) {
        if (documentId != null && syncedDocument?.documentId == documentId) {
            // the open document moved: the manager already follows its new id
            _state.update { old -> old.copy(documentId = documentId) }
            scope.launch { host.loadWikiTree(documentId) }
            load(documentId, connect = false)
            return
        }
        if (documentId != null && !environment.offlineMode.value) {
            if (withContext(environment.ioDispatcher) { !environment.hasLocalCopy(documentId) }) {
                // a copy held under an old id of this document (moved while the app was
                // offline) must be re-keyed before the copy is loaded, or its edits would not
                // show up
                val currentId = withTimeoutOrNull(REDIRECT_TIMEOUT_MS) {
                    withContext(environment.ioDispatcher) { environment.followRedirect(documentId) }
                }
                if (currentId != null && currentId != documentId) {
                    _documentId.value = currentId
                    return
                }
            } else {
                // the copy is here; other moved copies are re-keyed meanwhile (sessions merge
                // what lands under their id, see DocumentSyncSession.persist)
                scope.launch(environment.ioDispatcher) { environment.followAllRedirects() }
            }
        }
        _state.update { old ->
            old.copy(
                documentId = documentId,
                loading = documentId != null && !environment.offlineMode.value,
                deletedOnServer = false,
                noOfflineVersion = false,
            )
        }
        if (documentId != null) scope.launch { host.loadWikiTree(documentId) }
        syncedDocument?.shutdown()
        // nothing may reach the old document while the next one loads
        syncedDocument = null
        text.unbind()
        localCopyAvailable.value = false
        readOnly.value = false
        documentLoadFailed = false
        updateOfflineBanner()

        if (documentId == null) return

        val backend = environment.currentBackend()
        val serverConfig = backend?.serverConfig
        if (serverConfig == null) {
            host.onBackendGone()
            return
        }
        backendId = backend.id

        val document = withContext(environment.ioDispatcher) {
            environment.createSyncedDocument(documentId, serverConfig, backend.id, listener(documentId, backend.id))
        }
        syncedDocument = document
        // show the local copy right away, it may hold edits made offline
        text.bind(document)
        localCopyAvailable.value = document.hasLocalCopy
        readOnly.value = document.readOnly

        load(documentId, connect = true)
    }

    private fun listener(documentId: String, backendId: Long) = object : AutomergeDocumentSyncManager.Listener {
        override fun onConnectionStatusChanged(connected: Boolean, closeCode: Int?, throwable: Throwable?) {
            val status = ConnectionStatus(connected, closeCode, throwable)
            if (status.isAuthenticationFailure) {
                // edits stay in the local copy and sync after the next sign-in
                environment.onUnauthenticated(backendId)
            }
            _connectionStatus.value = status
            updateOfflineBanner()
            // without a local copy the text is still on its way. A failed connection
            // ends the loading too: it keeps retrying, and the snackbar says so.
            _state.update { old -> old.copy(loading = connected && !localCopyAvailable.value) }
            if (connected && documentLoadFailed) reloadAfterServerIsBack()
        }

        override fun onTextReplaced(text: String) {
            this@DocumentSession.text.onDocumentChanged()
            localCopyAvailable.value = true
            _state.update { old -> old.copy(loading = false, noOfflineVersion = false) }

            scope.launch {
                environment.updateContentCache(syncedDocument?.documentId ?: documentId, text)
            }
        }

        override fun onRemoteChange(text: String, splices: List<TextSplice>) {
            this@DocumentSession.text.onDocumentChanged(splices)
            host.onRemoteChange()
        }

        override fun onReadOnlyChanged(readOnly: Boolean) {
            this@DocumentSession.readOnly.value = readOnly
            if (readOnly) host.onReadOnly()
        }

        override fun onPeersChanged(peers: List<RemotePeer>) {
            text.onPeersChanged(peers)
        }

        override fun onDocumentRenamed(oldDocumentId: String, newDocumentId: String) {
            // the copy moved with the document; follow it without reconnecting the editor
            _documentId.value = newDocumentId
        }

        override fun onDocumentDeleted() {
            // the local copy (and any unsynced edits) stays available
            _state.update { old -> old.copy(deletedOnServer = true) }
        }
    }

    /**
     * @param connect false when the document is connected already (after a move)
     */
    private suspend fun load(documentId: String, connect: Boolean) {
        val offlineMode = environment.offlineMode.value
        // the text comes through the connection, it does not wait for the item tree
        if (connect && !offlineMode) reconnect()

        val result = environment.loadDocument(documentId) { cached ->
            if (connect) showCachedDocument(cached)
        }
        loadedDocument = result.document
        updateTitle(result.document?.name ?: "")
        when {
            result.error != null -> {
                Timber.e(result.error) { "Error loading document resource" }
                val connected = syncedDocument?.isConnected == true
                documentLoadFailed = !connected
                updateOfflineBanner()
                // a local copy is shown and editable already, and its edits sync later. The
                // connection keeps trying, and ends the offline state as soon as the server answers
                if (!localCopyAvailable.value && !connected) showOfflineVersionIfPossible()
            }

            connect && offlineMode -> showOfflineVersionIfPossible()
        }
    }

    /**
     * Shows the text of the offline cache until the server sends the document. It cannot be
     * edited: without a local copy the edit mode stays off. The text of a local copy wins.
     */
    private fun showCachedDocument(cached: CachedDocument?) {
        cached ?: return
        updateTitle(cached.name)
        cached.text?.let { text.showDetachedText(it) }
    }

    /**
     * The document could not be loaded when it was opened. Catches up on what is missing, the
     * sync manager already brought the text up to date.
     */
    private fun reloadAfterServerIsBack() {
        val documentId = _documentId.value ?: return
        documentLoadFailed = false
        scope.launch {
            host.loadWikiTree(documentId)
            load(documentId, connect = false)
        }
    }

    private fun showOfflineVersionIfPossible() {
        // prefer the local Automerge copy, it includes edits made offline
        val cachedContent = syncedDocument?.text ?: loadedDocument?.text
        if (cachedContent == null) {
            _state.update { old -> old.copy(noOfflineVersion = true) }
            return
        }
        host.onOfflineVersionShown()
        text.showDetachedText(cachedContent)
    }

    private fun updateTitle(title: String) {
        _state.update { old -> old.copy(title = title) }
    }

    private fun updateOfflineBanner() {
        val visible = isOfflineBannerVisible(
            offlineModeEnabled = environment.offlineMode.value,
            documentLoadFailed = documentLoadFailed,
            connectionStatus = _connectionStatus.value,
        )
        _state.update { old -> old.copy(offlineBannerVisible = visible) }
    }

    /**
     * Disconnects from the server (if necessary) and tries to reestablish a connection
     */
    fun reconnect() {
        _state.update { old -> old.copy(loading = syncedDocument != null) }
        syncedDocument?.connect()
    }

    private fun disconnect(reason: String) {
        syncedDocument?.disconnect(reason = reason)
        _connectionStatus.value = ConnectionStatus(connected = false)
        _state.update { old -> old.copy(loading = false) }
    }

    fun onLocalSelectionChanged(anchor: Int, head: Int) {
        syncedDocument?.onLocalSelectionChanged(anchor, head)
    }

    /** Writes the local copy to disk, so no edit is lost if the app gets killed */
    fun persist() {
        syncedDocument?.persistNow()
    }

    fun close() {
        syncedDocument?.shutdown()
    }
}
