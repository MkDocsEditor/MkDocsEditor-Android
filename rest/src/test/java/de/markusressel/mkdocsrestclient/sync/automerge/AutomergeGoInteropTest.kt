package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.applySplices
import org.automerge.SyncState
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Interop between automerge-java and automerge-go, the library of the backend.
 *
 * The fixtures in `src/test/resources/automerge-go` were created by
 * `tools/automerge-go-fixtures` (`go run . gen <dir>`) with the automerge-go version
 * the backend uses:
 * - `go-base.automerge`: a server document as the backend creates it
 * - `go-edited.automerge` / `go-edited.txt`: the same document after a server edit
 * - `go-sync-*.bin`: the sync messages automerge-go sends to a peer holding `go-base`
 *
 * Two optional checks need Go and run only when their Gradle property is set:
 * - `-Pautomerge.interop.go=<binary>`: a live sync session with the tool's `serve` mode
 * - `-Pautomerge.interop.out=<dir>`: writes `java-edited.automerge` for `go run . check <dir>`
 */
class AutomergeGoInteropTest {

    private fun fixture(name: String): ByteArray {
        return requireNotNull(javaClass.classLoader?.getResourceAsStream("automerge-go/$name")) {
            "missing fixture $name"
        }.use { it.readBytes() }
    }

    private val actor = ByteArray(16) { 0x42 }

    private val baseText = "Hällo Wörld 😀\n"
    private val mergedText = "Hey! Hällo Wörld 😀 from Go\n"

    private fun locallyEditedBase(): AutomergeTextDocument {
        val doc = AutomergeTextDocument.load(fixture("go-base.automerge"), actor)
        doc.applyLocalText("Hey! " + doc.text)
        return doc
    }

    @Test
    fun `a document saved by automerge-go loads with its text`() {
        val doc = AutomergeTextDocument.load(fixture("go-base.automerge"), actor)
        assertEquals(baseText, doc.text)
        assertEquals(actor.toList(), doc.actorId.toList())
    }

    @Test
    fun `sync messages of automerge-go merge with local edits`() {
        val doc = locallyEditedBase()

        val syncState = SyncState()
        var text = doc.text
        listOf("go-sync-0.bin", "go-sync-1.bin").forEach { name ->
            val splices = doc.receiveSyncMessage(syncState, fixture(name))
            text = text.applySplices(splices)
        }

        assertEquals("Hey! " + String(fixture("go-edited.txt"), Charsets.UTF_8), doc.text)
        assertEquals(mergedText, doc.text)
        assertEquals(mergedText, text)
    }

    @Test
    fun `replies to automerge-go use the sync message format it understands`() {
        // automerge-go (automerge-rs 0.5) only parses message type 0x42. Newer cores
        // only send 0x43 to peers that announce support for it.
        val doc = locallyEditedBase()
        val syncState = SyncState()
        doc.receiveSyncMessage(syncState, fixture("go-sync-0.bin"))

        val reply = requireNotNull(doc.generateSyncMessage(syncState))
        assertEquals(0x42, reply[0].toInt())
    }

    @Test
    fun `a document edited by automerge-go merges with local edits`() {
        val local = locallyEditedBase()
        local.merge(AutomergeTextDocument.load(fixture("go-edited.automerge")))
        assertEquals(mergedText, local.text)
    }

    @Test
    fun `live sync session with automerge-go`() {
        val binary = System.getProperty("automerge.interop.go") ?: return
        val fixtureDir = requireNotNull(
            File(requireNotNull(javaClass.classLoader?.getResource("automerge-go/go-base.automerge")).toURI()).parentFile
        )

        val process = ProcessBuilder(binary, "serve", fixtureDir.absolutePath)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val input = process.inputStream.bufferedReader()
        val output = process.outputStream.bufferedWriter()

        val doc = locallyEditedBase()
        val syncState = SyncState()
        var serverText: String? = null
        while (serverText == null) {
            val line = requireNotNull(input.readLine()) { "automerge-go exited" }
            when {
                line.startsWith("TEXT ") -> serverText = String(Base64.getDecoder().decode(line.substring(5)), Charsets.UTF_8)
                line.startsWith("MSG ") -> doc.receiveSyncMessage(syncState, Base64.getDecoder().decode(line.substring(4)))
            }
            if (serverText == null) {
                val reply = doc.generateSyncMessage(syncState)
                output.write(if (reply != null) "MSG " + Base64.getEncoder().encodeToString(reply) else "NONE")
                output.newLine()
                output.flush()
            }
        }
        process.waitFor()

        assertEquals(mergedText, doc.text)
        assertEquals(mergedText, serverText)
    }

    @Test
    fun `write a document for the automerge-go check`() {
        val outDir = System.getProperty("automerge.interop.out") ?: return
        val doc = AutomergeTextDocument.load(fixture("go-base.automerge"), actor)
        doc.applyLocalText(doc.text.replace("Wörld", "Android"))
        File(outDir).apply { mkdirs() }.resolve("java-edited.automerge").writeBytes(doc.save())
    }
}
