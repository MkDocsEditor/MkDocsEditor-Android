package de.markusressel.mkdocseditor.feature.qr

import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import de.markusressel.mkdocseditor.feature.qr.domain.QrKey
import de.markusressel.mkdocseditor.feature.qr.domain.QrKeys
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals

/** QR code keys go into the front matter through the editor, as one edit of the Automerge document like typing */
class QrFrontMatterEditTest {

    private class TestDocument(text: String) : EditableTextDocument {
        val automerge: AutomergeTextDocument = run {
            val document = Document()
            document.startTransaction().use { tx ->
                val content = tx.set(ObjectId.ROOT, AutomergeTextDocument.CONTENT_KEY, ObjectType.TEXT)
                tx.spliceText(content, 0, 0, text)
                tx.commit()
            }
            AutomergeTextDocument.fromDocument(document)
        }
        val splices = mutableListOf<TextSplice>()
        override val text: String get() = automerge.text
        override val version: DocumentVersion get() = automerge.version
        override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>): DocumentVersion? {
            this.splices += splices
            return automerge.applySplicesAt(base, baseText, splices)
        }
    }

    /** reading, not editing: the dialog changes the page also outside of the edit mode */
    private fun controllerFor(document: TestDocument) = EditorTextController(editModeActive = { false }).also { it.bind(document) }

    private fun EditorTextController.setQrKeys(keys: List<QrKey>) = changeTo(QrKeys.withKeys(text, keys))

    @Test
    fun `a page without front matter gets one`() {
        val document = TestDocument("# Waschmaschine\n\nText\n")
        val controller = controllerFor(document)

        controller.setQrKeys(listOf(QrKey("waschmaschine")))

        assertEquals("---\nqr: waschmaschine\n---\n\n# Waschmaschine\n\nText\n", document.text)
        assertEquals(listOf(TextSplice(0, 0, "---\nqr: waschmaschine\n---\n\n")), document.splices)
    }

    @Test
    fun `the rest of the front matter and of the page stays as it is`() {
        val text = "---\ntitle: Waschmaschine\ntags: [haus, keller]\n---\n# Text\n"
        val document = TestDocument(text)
        val controller = controllerFor(document)

        controller.setQrKeys(listOf(QrKey("wm", "Keller")))
        assertEquals(
            "---\ntitle: Waschmaschine\ntags: [haus, keller]\nqr:\n  - key: wm\n    label: \"Keller\"\n---\n# Text\n",
            document.text,
        )
        assertEquals(listOf(QrKey("wm", "Keller")), QrKeys.keysOf(document.text))

        controller.setQrKeys(emptyList())
        assertEquals(text, document.text)
    }

    @Test
    fun `only the changed part goes into the document, so a concurrent edit stays`() {
        val document = TestDocument("---\nqr: a\n---\nText")
        val other = AutomergeTextDocument.fromDocument(document.automerge.document.fork())
        val controller = controllerFor(document)

        controller.setQrKeys(listOf(QrKey("a"), QrKey("b")))
        other.applyLocalText("---\nqr: a\n---\nText, more text")
        val remote = document.automerge.merge(other)
        controller.onRemoteChange(document.text, remote)

        assertEquals("---\nqr: [a, b]\n---\nText, more text", document.text)
        assertEquals(document.text, controller.text)
        assertEquals(1, document.splices.size)
    }

    @Test
    fun `the selection stays on the text it was on`() {
        val document = TestDocument("# Title\nBody")
        val controller = controllerFor(document)
        controller.select(8, 12)

        controller.setQrKeys(listOf(QrKey("wm")))

        assertEquals("Body", controller.selectedText)
    }
}
