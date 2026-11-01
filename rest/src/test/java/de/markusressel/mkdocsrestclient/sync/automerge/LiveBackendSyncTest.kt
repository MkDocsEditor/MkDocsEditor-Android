package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.applySplices
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionHandler
import de.markusressel.mkdocsrestclient.sync.websocket.WebsocketConnectionListener
import de.markusressel.mkdocsrestclient.reference.MoveResultModel
import de.markusressel.mkdocsrestclient.reference.MovedModel
import de.markusressel.mkdocsrestclient.toEntity
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
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
 * It edits the first document of the root section and creates and moves test documents, so
 * use a test wiki. The move test needs a backend with `POST /move/` (MkDocsEditor-Backend #49).
 */
class LiveBackendSyncTest {

    private val baseUrl = System.getProperty("mkdocs.backend.url")
    private val users = System.getProperty("mkdocs.backend.users")?.split(",")?.map {
        val (user, password) = it.split(":", limit = 2)
        TestUser(user, password)
    }

    /**
     * A user of the test backend. Authenticates with HTTP Basic, which the backend accepts
     * with `server.auth.legacyBasicAuth: true`.
     */
    private data class TestUser(val username: String, val password: String) {
        val authorization: String = Credentials.basic(username, password)
    }

    private val http = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /**
     * A session connected over a real websocket. All session calls run on one thread.
     */
    private inner class LiveClient(
        val auth: TestUser,
        documentId: String,
        store: LocalDocumentStore,
    ) : DocumentSyncSession.Listener {
        private val executor = Executors.newSingleThreadExecutor()
        var editorText: String? = null
        var peers: List<RemotePeer> = emptyList()
        var connected = false
        var closeCode: Int? = null

        val session = run(executor) { DocumentSyncSession(documentId, store, this) }
        private val connection = WebsocketConnectionHandler({ auth.authorization }, http)

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

        private fun wsUrl(documentId: String) =
            baseUrl!!.replaceFirst("http", "ws").trimEnd('/') + "/document/$documentId/ws/"

        fun connect() {
            connection.connect(wsUrl(session.documentId))
            waitFor("${auth.username} syncing") { session.isSyncing }
        }

        /**
         * Connects with an old id of a moved document: the server answers `document-renamed`
         * and closes, the session re-keys its copy, then this connects again with the new id
         */
        fun connectFollowingMove(newId: String) {
            val oldId = session.documentId
            connection.connect(wsUrl(oldId))
            waitFor("${auth.username} learns the new id") { session.documentId == newId }
            waitFor("${auth.username} closed with 4009") { !connected && closeCode == 4009 }
            connect()
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

    private fun get(path: String, auth: TestUser): String {
        val request = Request.Builder()
            .url(baseUrl!!.trimEnd('/') + path)
            .header("Authorization", auth.authorization)
            .build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GET $path: ${response.code}" }
            response.body.string()
        }
    }

    private fun send(method: String, path: String, auth: TestUser, json: String): String {
        val request = Request.Builder()
            .url(baseUrl!!.trimEnd('/') + path)
            .header("Authorization", auth.authorization)
            .header("X-Requested-With", "MkDocsEditor-Android")
            .method(method, json.toRequestBody("application/json".toMediaType()))
            .build()
        return http.newCall(request).execute().use { response ->
            val body = response.body.string()
            check(response.isSuccessful) { "$method $path: ${response.code} $body" }
            body
        }
    }

    private fun idOf(json: String): String = Regex("\"id\":\\s*\"([^\"]+)\"").find(json)!!.groupValues[1]

    private fun firstDocumentId(auth: TestUser): String {
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

    /**
     * The document moves on the server while alice holds it offline with unsynced edits. Her
     * copy follows the move and the edits reach the moved document, merged with the link the
     * move rewrote. Then the same with the redirect lookup the app runs before opening a
     * document (`GET /moved/{id}/`).
     */
    /**
     * Edits made on an older state of the document, as an editor does that has not shown a
     * remote change yet: their Automerge changes are concurrent to changes the client already has.
     */
    @Test
    fun `edits on an older state merge on a live backend`() {
        if (baseUrl == null || users == null) return
        val (alice, bob) = users
        val name = "concurrent-${System.currentTimeMillis().toString(36)}"
        val documentId = idOf(send("POST", "/document/", alice, """{"parent":"root","name":"$name"}"""))
        val a = LiveClient(alice, documentId, InMemoryDocumentStore())
        val b = LiveClient(bob, documentId, InMemoryDocumentStore())
        try {
            a.connect()
            b.connect()
            a.type("start\n")
            b.waitFor("b got the text") { b.session.text == "start\n" }

            var version = a.read { version!! }
            var baseText = a.read { text!! }
            b.type("remote start\n")
            a.waitFor("a got the remote change") { a.session.text == "remote start\n" }

            // a is still on the old state and types there, also several times in a row
            "abc".forEach { c ->
                val edit = TextSplice(baseText.length, 0, c.toString())
                version = a.read { applyLocalEdit(version, baseText, listOf(edit))!! }
                baseText = edit.applyTo(baseText)
                a.read { flushSync() }
            }

            val expected = "remote start\nabc"
            a.waitFor("a merged") { a.session.text == expected }
            b.waitFor("b merged") { b.session.text == expected }
            a.waitFor("the server has it") { get("/document/$documentId/content/", alice) == expected }
        } finally {
            a.close()
            b.close()
        }
    }

    @Test
    fun `offline edits survive a move on the server`() {
        if (baseUrl == null || users == null) return
        val (aliceAuth, bobAuth) = users
        val dir: File = createTempDirectory("live-move").toFile()
        val suffix = System.currentTimeMillis().toString(36)

        try {
            val section = idOf(send("POST", "/section/", bobAuth, """{"parent":"root","name":"move-$suffix"}"""))
            val documentId = idOf(send("POST", "/document/", bobAuth, """{"parent":"$section","name":"page"}"""))
            val start = "# Page\n\n[home](../index.md)\n"

            // alice writes the start, goes offline and keeps editing
            val alice = LiveClient(aliceAuth, documentId, FileDocumentStore(File(dir, "alice")))
            alice.connect()
            alice.type(start)
            alice.waitFor("server has the start") { get("/document/$documentId/content/", aliceAuth) == start }
            alice.disconnect()
            alice.type(start + "offline by alice\n")
            alice.read { persist() }
            alice.close()

            // bob moves the page to the top level, which rewrites its link
            val moved = send(
                "POST", "/move/", bobAuth,
                """{"type":"document","id":"$documentId","parent":"root","name":"moved-$suffix","dryRun":false}""",
            ).toEntity<MoveResultModel>()
            val newId = moved.newId
            assertEquals("moved-$suffix.md", moved.newPath)
            assertEquals("[home](index.md)", moved.documents.single().edits.single().after)
            assertEquals(newId, get("/moved/$documentId/", aliceAuth).toEntity<MovedModel>().item.id)

            // alice comes back with the old id
            val alice2 = LiveClient(aliceAuth, documentId, FileDocumentStore(File(dir, "alice")))
            assertEquals(start + "offline by alice\n", alice2.editorText)
            alice2.connectFollowingMove(newId)
            val expected = "# Page\n\n[home](index.md)\noffline by alice\n"
            alice2.waitFor("alice2 merged") { alice2.session.text == expected }
            assertEquals(expected, alice2.editorText)
            assertEquals(listOf(newId), FileDocumentStore(File(dir, "alice")).ids())
            alice2.waitFor("server has the offline edit") { get("/document/$newId/content/", aliceAuth) == expected }
            alice2.close()

            // second round: bob moves it back into the folder while alice is offline again;
            // the app follows the redirect before it opens the document by its new id
            val store = FileDocumentStore(File(dir, "alice"))
            run {
                val offline = LiveClient(aliceAuth, newId, store)
                offline.type(expected + "second offline edit\n")
                offline.read { persist() }
                offline.close()
            }
            val backId = send(
                "POST", "/move/", bobAuth,
                """{"type":"document","id":"$newId","parent":"$section","name":"page","dryRun":false}""",
            ).toEntity<MoveResultModel>().newId
            val redirects = LocalCopyRedirects(store) { id ->
                val request = Request.Builder().url(baseUrl.trimEnd('/') + "/moved/$id/")
                    .header("Authorization", aliceAuth.authorization).build()
                http.newCall(request).execute().use { response ->
                    when (response.code) {
                        200 -> LocalCopyRedirects.Lookup.Moved(response.body.string().toEntity<MovedModel>().item.id)
                        404 -> LocalCopyRedirects.Lookup.NotMoved
                        else -> LocalCopyRedirects.Lookup.Unknown
                    }
                }
            }
            assertEquals(backId, runBlocking { redirects.beforeOpening(backId) })
            assertEquals(listOf(backId), store.ids())
            val alice4 = LiveClient(aliceAuth, backId, store)
            alice4.connect()
            val expected2 = "# Page\n\n[home](../index.md)\noffline by alice\nsecond offline edit\n"
            alice4.waitFor("alice4 merged") { alice4.session.text == expected2 }
            alice4.waitFor("server has the second offline edit") { get("/document/$backId/content/", aliceAuth) == expected2 }
            alice4.close()
        } finally {
            dir.deleteRecursively()
        }
    }
}
