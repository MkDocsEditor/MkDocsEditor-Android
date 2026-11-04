package de.markusressel.mkdocsrestclient.sync.automerge

import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthorColorsTest {

    private val directory = createTempDirectory("author-colors").toFile()

    @AfterTest
    fun cleanup() {
        directory.deleteRecursively()
    }

    @Test
    fun `colors of users who are not online and of this user are known`() {
        val server = FakeSyncServer("text")
        server.colors["alice"] = "#111111"
        server.colors["bob"] = "#222222"
        val offline = server.connect(session("offline"), username = "alice")
        server.pump()
        server.disconnect(offline)

        var colors = emptyMap<String, String?>()
        val session = DocumentSyncSession(
            server.documentId,
            FileDocumentStore(directory.resolve("bob")),
            object : DocumentSyncSession.Listener {
                override fun onAuthorColorsChanged(colors2: Map<String, String?>) {
                    colors = colors2
                }
            },
        )
        server.connect(session, username = "bob")
        server.pump()

        assertEquals(mapOf("alice" to "#111111", "bob" to "#222222"), colors)
    }

    private fun session(name: String) =
        DocumentSyncSession("doc-1", FileDocumentStore(directory.resolve(name)), object : DocumentSyncSession.Listener {})
}
