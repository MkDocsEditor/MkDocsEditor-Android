package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.applySplices
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end test against a running MkDocsEditor backend. Skipped unless these
 * Gradle properties are set:
 *
 * - `-Pmkdocs.backend.url=http://127.0.0.1:7413`
 * - `-Pmkdocs.backend.users=alice:password,bob:password` (two editors)
 *
 * It edits the first document of the root section, so use a test wiki.
 */
class LiveBackendSyncTest {

    private val baseUrl = System.getProperty("mkdocs.backend.url")
    private val users = System.getProperty("mkdocs.backend.users")?.split(",")?.map {
        val (user, password) = it.split(":", limit = 2)
        BasicAuthConfig(user, password)
    }

    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /**
     * A session connected over a real websocket. All session calls run on one thread.
     */
    private inner class LiveClient(
        val auth: BasicAuthConfig,
        documentId: String,
        store: LocalDocumentStore,
    ) : DocumentSyncSession.Listener {
        private val executor = Executors.newSingleThreadExecutor()
        var editorText: String? = null
        var peers: List<RemotePeer> = emptyList()
        var connected = false
        var closeCode: Int? = null

        val session = run(executor) { DocumentSyncSession(documentId, store, this) }
        private val connection = WebsocketConnectionHandler(auth, http)

        init {
            editorText = session.text
            connection.setListener(object : WebsocketConnectionListener {
                override fun onOpen() = executor.execute {
                    connected = true
                    session.onConnected(send = { connection.send(it) })
                }

                override fun onMessage(text: String) = executor.execute { session.onMessage(text) }

                override fun onClosed(code: Int?, httpStatus: Int?, throwable: Throwable?) = executor.execute {
                    connected = false
                    closeCode = code ?: httpStatus
                    session.onDisconnected()
                }
            })
        }

        fun connect() {
            val wsUrl = baseUrl!!.replaceFirst("http", "ws").trimEnd('/') + "/document/${session.documentId}/ws/"
            connection.connect(wsUrl)
            waitFor("${auth.username} syncing") { session.isSyncing }
        }

        fun disconnect() {
            connection.disconnect(1000, "test")
            run(executor) { session.onDisconnected() }
        }

        fun type(newText: String) = run(executor) {
            assertTrue(session.onLocalTextChanged(newText))
            editorText = newText
            session.flushSync()
        }

        fun select(anchor: Int, head: Int) = run(executor) { session.updateLocalSelection(anchor, head) }

        fun <T> read(block: DocumentSyncSession.() -> T): T = run(executor) { session.block() }

        fun waitFor(what: String, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + 10_000
            while (!run(executor, condition)) {
                check(System.currentTimeMillis() < deadline) { "timeout waiting for $what (close code $closeCode)" }
                Thread.sleep(20)
            }
        }

        fun close() {
            connection.disconnect(1000, "test done")
            executor.shutdown()
        }

        override fun onTextReplaced(text: String) {
            editorText = text
        }

        override fun onRemoteChange(text: String, splices: List<TextSplice>) {
            assertEquals(text, editorText!!.applySplices(splices))
            editorText = text
        }

        override fun onPeersChanged(peers: List<RemotePeer>) {
            this.peers = peers
        }
    }

    private fun <T> run(executor: java.util.concurrent.ExecutorService, block: () -> T): T =
        executor.submit<T> { block() }.get(10, TimeUnit.SECONDS)

    private fun get(path: String, auth: BasicAuthConfig): String {
        val request = Request.Builder()
            .url(baseUrl!!.trimEnd('/') + path)
            .header("Authorization", Credentials.basic(auth.username, auth.password))
            .build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GET $path: ${response.code}" }
            response.body.string()
        }
    }

    private fun firstDocumentId(auth: BasicAuthConfig): String {
        val tree = get("/section/", auth)
        return requireNotNull(Regex("\"type\":\\s*\"document\",\\s*\"id\":\\s*\"([^\"]+)\"").find(tree)) {
            "no document in the root section"
        }.groupValues[1]
    }

    @Test
    fun `edits, presence and offline edits against a live backend`() {
        if (baseUrl == null || users == null) return
        val (aliceAuth, bobAuth) = users
        val documentId = firstDocumentId(aliceAuth)
        val dir: File = createTempDirectory("live-sync").toFile()

        try {
            val alice = LiveClient(aliceAuth, documentId, FileDocumentStore(File(dir, "alice")))
            val bob = LiveClient(bobAuth, documentId, FileDocumentStore(File(dir, "bob")))
            alice.connect()
            bob.connect()
            val start = alice.read { text!! }
            bob.waitFor("bob has the text") { bob.session.text == start }

            // concurrent edits from both users, including non-BMP characters
            alice.type("A 😀 " + start)
            bob.type(bob.read { text!! } + "B ä\n")
            val merged = "A 😀 " + start + "B ä\n"
            alice.waitFor("alice merged") { alice.session.text == merged }
            bob.waitFor("bob merged") { bob.session.text == merged }
            assertEquals(merged, alice.editorText)
            assertEquals(merged, bob.editorText)

            // presence: bob's caret before "B"
            val caret = merged.indexOf("B ä")
            bob.select(caret, caret)
            alice.waitFor("alice sees bob") { alice.peers.any { it.username == bobAuth.username && it.selections.isNotEmpty() } }
            assertEquals(caret, alice.peers.first { it.username == bobAuth.username }.selections.first().head)

            // alice goes offline, edits, persists, and her app process "dies"
            alice.disconnect()
            alice.type(alice.read { text!! } + "offline by alice\n")
            alice.read { persist() }
            alice.close()

            // bob edits meanwhile
            bob.type("bob was here\n" + bob.read { text!! })

            // alice restarts with a new store instance on the same files and reconnects
            val alice2 = LiveClient(aliceAuth, documentId, FileDocumentStore(File(dir, "alice")))
            assertEquals(merged + "offline by alice\n", alice2.editorText)
            alice2.connect()

            val expected = "bob was here\n" + merged + "offline by alice\n"
            alice2.waitFor("alice2 merged") { alice2.session.text == expected }
            bob.waitFor("bob got the offline edit") { bob.session.text == expected }
            assertEquals(expected, alice2.editorText)

            // the server's live text has both edits too
            assertEquals(expected, get("/document/$documentId/content/", aliceAuth))

            alice2.close()
            bob.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
