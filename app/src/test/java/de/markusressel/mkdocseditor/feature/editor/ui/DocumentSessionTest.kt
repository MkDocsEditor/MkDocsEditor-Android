package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.auth.backendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.SyncedDocument
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.CloseCode
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentSessionTest {

    private class FakeDocument(
        override var documentId: String,
        override var text: String? = null,
        val listener: AutomergeDocumentSyncManager.Listener,
    ) : SyncedDocument {
        override val version: DocumentVersion? = null
        override val hasLocalCopy: Boolean get() = text != null
        override var readOnly = false
        override var isConnected = false
        var connects = 0
        val disconnects = mutableListOf<String>()
        var persists = 0
        var shutDown = false

        override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) = null
        override fun splicesSince(version: DocumentVersion, text: String) = emptyList<TextSplice>()
        override fun connect() {
            connects++
        }

        override fun disconnect(reason: String) {
            disconnects += reason
        }

        override fun onLocalSelectionChanged(anchor: Int?, head: Int?) {}
        override fun persistNow() {
            persists++
        }

        override fun shutdown() {
            shutDown = true
        }

        /** the connection came up */
        fun connected() {
            isConnected = true
            listener.onConnectionStatusChanged(true)
        }

        /** the server sent the document */
        fun textArrived(text: String) {
            this.text = text
            listener.onTextReplaced(text)
        }
    }

    private inner class FakeEnvironment(scope: TestScope) : DocumentSessionEnvironment {
        override val ioDispatcher = UnconfinedTestDispatcher(scope.testScheduler)
        override val offlineMode = MutableStateFlow(false)
        override var connectsAutomatically = false
        override val sessionChanges = MutableSharedFlow<Long>()

        var signedIn = true
        val unauthenticated = mutableListOf<Long>()
        var backend: BackendConfig? = backendConfig(1)
        val documents = mutableListOf<FakeDocument>()
        var localCopyText: String? = null
        var localCopyInStore = true
        var redirects = mapOf<String, String>()
        var followedAll = 0
        var cached: CachedDocument? = null
        var loadAnswer: CompletableDeferred<DocumentLoad>? = null
        var loadCount = 0
        val cacheUpdates = mutableListOf<Pair<String, String>>()

        override fun isSignedIn(backendId: Long) = signedIn
        override fun onUnauthenticated(backendId: Long) {
            unauthenticated += backendId
        }

        override suspend fun currentBackend() = backend

        override fun createSyncedDocument(
            documentId: String,
            serverConfig: BackendServerConfig,
            backendId: Long,
            listener: AutomergeDocumentSyncManager.Listener,
        ): SyncedDocument = FakeDocument(documentId, localCopyText, listener).also { documents += it }

        override fun hasLocalCopy(documentId: String) = localCopyInStore
        override suspend fun followRedirect(documentId: String) = redirects[documentId] ?: documentId
        override suspend fun followAllRedirects() {
            followedAll++
        }

        override suspend fun loadDocument(documentId: String, onCached: (CachedDocument?) -> Unit): DocumentLoad {
            loadCount++
            onCached(cached)
            return loadAnswer?.await() ?: DocumentLoad(cached)
        }

        override suspend fun updateContentCache(documentId: String, text: String) {
            cacheUpdates += documentId to text
        }
    }

    private class FakeHost : DocumentSession.Host {
        val treesLoaded = mutableListOf<String>()
        var backendGone = false
        var offlineVersions = 0
        var readOnly = 0
        var remoteChanges = 0

        override suspend fun loadWikiTree(documentId: String) {
            treesLoaded += documentId
        }

        override suspend fun onBackendGone() {
            backendGone = true
        }

        override fun onOfflineVersionShown() {
            offlineVersions++
        }

        override fun onReadOnly() {
            readOnly++
        }

        override fun onRemoteChange() {
            remoteChanges++
        }
    }

    private inner class Fixture(scope: TestScope) {
        val environment = FakeEnvironment(scope)
        val host = FakeHost()
        val text = EditorTextController(scope.backgroundScope, { scope.currentTime }, { false })
        val session = DocumentSession(scope.backgroundScope, text, environment, host)
        val document get() = environment.documents.last()
        val fieldText get() = text.field.value.value.text
        val state get() = session.state.value
    }

    private fun test(block: suspend TestScope.(Fixture) -> Unit) = runTest(UnconfinedTestDispatcher()) {
        block(Fixture(this))
    }

    private fun Fixture.open(documentId: String = "page") {
        session.start()
        session.setDocument(documentId)
    }

    @Test
    fun `opening connects right away, without waiting for the item tree`() = test { f ->
        f.environment.loadAnswer = CompletableDeferred()
        f.open()
        assertEquals(1, f.document.connects)
        assertTrue(f.state.loading)
        assertEquals("page", f.state.documentId)
        assertEquals(listOf("page"), f.host.treesLoaded)
    }

    @Test
    fun `the cached text and title show while the document loads, but can not be edited`() = test { f ->
        f.environment.cached = CachedDocument("Title", "cached text")
        f.environment.loadAnswer = CompletableDeferred()
        f.open()
        assertEquals("cached text", f.fieldText)
        assertEquals("Title", f.state.title)
        assertFalse(f.session.editable.value)
    }

    @Test
    fun `the text of the server replaces the cached text, ends the loading and allows editing`() = test { f ->
        f.environment.cached = CachedDocument("Title", "cached text")
        f.open()
        f.document.connected()
        assertTrue(f.state.loading, "connected, but the text is still on its way")

        f.document.textArrived("server text")

        assertEquals("server text", f.fieldText)
        assertFalse(f.state.loading)
        assertTrue(f.session.editable.value)
        assertEquals(listOf("page" to "server text"), f.environment.cacheUpdates)
    }

    @Test
    fun `a local copy wins over the cached text and can be edited right away`() = test { f ->
        f.environment.localCopyText = "my offline edits"
        f.environment.cached = CachedDocument("Title", "cached text")
        f.open()
        assertEquals("my offline edits", f.fieldText)
        assertTrue(f.session.editable.value)

        f.document.connected()
        assertFalse(f.state.loading, "with a local copy, the connection is all it waits for")
    }

    @Test
    fun `a failed connection ends the loading`() = test { f ->
        f.open()
        f.document.listener.onConnectionStatusChanged(false, 1006)
        assertFalse(f.state.loading)
        assertEquals(ConnectionStatus(false, 1006), f.session.connectionStatus.value)
    }

    @Test
    fun `offline mode shows the cached version and does not connect`() = test { f ->
        f.environment.offlineMode.value = true
        f.environment.cached = CachedDocument("Title", "cached text")
        f.open()
        assertEquals(0, f.document.connects)
        assertFalse(f.state.loading)
        assertTrue(f.state.offlineBannerVisible)
        assertEquals("cached text", f.fieldText)
        assertEquals(1, f.host.offlineVersions)
    }

    @Test
    fun `a failed load without a connection falls back to the cache, the connection ends that`() = test { f ->
        f.environment.cached = CachedDocument("Title", "cached text")
        f.environment.loadAnswer = CompletableDeferred(DocumentLoad(f.environment.cached, IOException("no server")))
        f.open()
        assertTrue(f.state.offlineBannerVisible)
        assertEquals(1, f.host.offlineVersions)
        assertEquals(1, f.environment.loadCount)

        f.environment.loadAnswer = null
        f.document.connected()

        assertFalse(f.state.offlineBannerVisible)
        assertEquals(2, f.environment.loadCount, "catches up on what is missing")
        assertEquals(1, f.document.connects, "the connection stays")
    }

    @Test
    fun `without a connection and a text it says so, until the text arrives`() = test { f ->
        f.environment.loadAnswer = CompletableDeferred(DocumentLoad(null, IOException("no server")))
        f.open()
        assertTrue(f.state.noOfflineVersion)
        assertEquals(0, f.host.offlineVersions)

        f.document.connected()
        f.document.textArrived("server text")
        assertFalse(f.state.noOfflineVersion)
    }

    @Test
    fun `the notices of a document do not carry over to the next one`() = test { f ->
        f.open("one")
        f.document.listener.onDocumentDeleted()
        assertTrue(f.state.deletedOnServer)

        f.session.setDocument("two")
        assertFalse(f.state.deletedOnServer)
    }

    @Test
    fun `a failed load with a connection is no offline state`() = test { f ->
        val answer = CompletableDeferred<DocumentLoad>()
        f.environment.loadAnswer = answer
        f.open()
        f.document.connected()

        answer.complete(DocumentLoad(null, IOException("hiccup")))

        assertFalse(f.state.offlineBannerVisible)
        assertEquals(0, f.host.offlineVersions)
    }

    @Test
    fun `a moved document keeps its connection`() = test { f ->
        f.open()
        val document = f.document
        document.documentId = "moved"
        document.listener.onDocumentRenamed("page", "moved")

        assertEquals(1, f.environment.documents.size)
        assertEquals(1, document.connects)
        assertEquals("moved", f.state.documentId)
        assertEquals(listOf("page", "moved"), f.host.treesLoaded)
    }

    @Test
    fun `a copy under an old id opens under the current id`() = test { f ->
        f.environment.localCopyInStore = false
        f.environment.redirects = mapOf("old" to "current")
        f.open("old")
        assertEquals(listOf("current"), f.environment.documents.map { it.documentId })
        assertEquals("current", f.state.documentId)
    }

    @Test
    fun `an existing copy looks for other moved copies on the side`() = test { f ->
        f.open()
        assertEquals(1, f.environment.followedAll)
    }

    @Test
    fun `another document ends the connection of the old one`() = test { f ->
        f.open("one")
        val first = f.document
        f.session.setDocument("two")
        assertTrue(first.shutDown)
        assertEquals("two", f.document.documentId)
        assertEquals(2, f.environment.documents.size)
    }

    @Test
    fun `without a backend the editor closes`() = test { f ->
        f.environment.backend = null
        f.open()
        assertTrue(f.host.backendGone)
        assertTrue(f.environment.documents.isEmpty())
    }

    @Test
    fun `a refused session tells the auth layer, and signing in reconnects`() = test { f ->
        f.open()
        f.document.listener.onConnectionStatusChanged(false, CloseCode.UNAUTHORIZED)
        assertEquals(listOf(1L), f.environment.unauthenticated)
        assertTrue(f.session.connectionStatus.value!!.isAuthenticationFailure)

        f.environment.sessionChanges.emit(99)
        assertEquals(1, f.document.connects, "another backend")

        f.environment.sessionChanges.emit(1)
        assertEquals(2, f.document.connects)
    }

    @Test
    fun `signing in does not reconnect an open connection`() = test { f ->
        f.open()
        f.document.connected()
        f.environment.sessionChanges.emit(1)
        assertEquals(1, f.document.connects)
    }

    @Test
    fun `switching on the offline mode disconnects, switching it off reconnects if configured`() = test { f ->
        f.environment.connectsAutomatically = true
        f.open()
        val connects = f.document.connects

        f.environment.offlineMode.value = true
        assertEquals(listOf("Offline mode activated"), f.document.disconnects)
        assertTrue(f.state.offlineBannerVisible)

        f.environment.offlineMode.value = false
        assertEquals(connects + 1, f.document.connects)
        assertFalse(f.state.offlineBannerVisible)
    }

    @Test
    fun `a read-only document can not be edited, until the role changes back`() = test { f ->
        f.open()
        f.document.textArrived("text")
        assertTrue(f.session.editable.value)

        f.document.listener.onReadOnlyChanged(true)
        assertFalse(f.session.editable.value)

        f.document.listener.onReadOnlyChanged(false)
        assertTrue(f.session.editable.value)
    }

    @Test
    fun `read-only, remote changes and deletion reach the host and the state`() = test { f ->
        f.open()
        f.document.textArrived("text")
        f.document.listener.onReadOnlyChanged(true)
        assertEquals(1, f.host.readOnly)

        f.document.text = "text!"
        f.document.listener.onRemoteChange("text!", emptyList())
        assertEquals(1, f.host.remoteChanges)
        assertEquals("text!", f.text.text)

        assertFalse(f.state.deletedOnServer)
        f.document.listener.onDocumentDeleted()
        assertTrue(f.state.deletedOnServer)
    }

    @Test
    fun `closing shuts the document down, persisting writes it`() = test { f ->
        f.open()
        f.session.persist()
        assertEquals(1, f.document.persists)
        f.session.close()
        assertTrue(f.document.shutDown)
    }
}
