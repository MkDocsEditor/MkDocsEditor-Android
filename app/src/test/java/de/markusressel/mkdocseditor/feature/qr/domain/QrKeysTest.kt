package de.markusressel.mkdocseditor.feature.qr.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The same cases as `markdown/qrKeys.spec.ts` of the web client, so both read and write the same */
class QrKeysTest {

    @Test
    fun `keys are read like the backend reads them`() {
        assertEquals(listOf(QrKey("waschmaschine")), QrKeys.keysOf("---\nqr: Waschmaschine \n---\n"))
        assertEquals(
            listOf(QrKey("wm", "Keller"), QrKey("42")),
            QrKeys.keysOf("---\nqr:\n  - key: wm\n    label: \"Keller\"\n  - 42\n  - -bad\n  - WM\n  - [x]\n---\n"),
        )
        assertEquals(emptyList(), QrKeys.keysOf("# Kein Front Matter\n"))
        assertNull(QrKeys.normalize("a".repeat(65)))
    }

    @Test
    fun `flow lists, quotes and comments are read`() {
        assertEquals(
            listOf(QrKey("a"), QrKey("b-2"), QrKey("7")),
            QrKeys.keysOf("---\ntitle: X\nqr: [a, \"B-2\", '7'] # stickers\ntags: [x]\n---\nText"),
        )
        assertEquals(listOf(QrKey("on")), QrKeys.keysOf("---\nqr: \"on\"\n---\n"))
    }

    @Test
    fun `values that are no text are no keys`() {
        assertEquals(emptyList(), QrKeys.keysOf("---\nqr: true\n---\n"))
        assertEquals(emptyList(), QrKeys.keysOf("---\nqr:\n---\n"))
        assertEquals(emptyList(), QrKeys.keysOf("---\nqr:\n  - key:\n    label: x\n---\n"))
    }

    @Test
    fun `a qr line outside of the front matter is text`() {
        assertEquals(emptyList(), QrKeys.keysOf("# Page\n\nqr: wm\n"))
        assertEquals(emptyList(), QrKeys.keysOf("---\ntitle: X\n---\nqr: wm\n"))
    }

    @Test
    fun `a key is suggested from the page name`() {
        assertEquals("waschmaschine-keller", QrKeys.suggest("Waschmaschine (Keller)"))
        assertEquals("groesse-fuesse", QrKeys.suggest("Größe & Füße"))
        assertEquals("cafe", QrKeys.suggest("Café"))
        assertEquals("aerger-ueber-oel", QrKeys.suggest("Ärger über Öl"))
        assertEquals("a".repeat(64), QrKeys.suggest("a".repeat(70)))
        // cut at 64, without the dash in front of the cut
        assertEquals("a".repeat(63), QrKeys.suggest("a".repeat(63) + " b"))
    }

    @Test
    fun `a front matter is added at the top`() {
        assertEquals("---\nqr: wm\n---\n\n# Waschmaschine\n", QrKeys.withKeys("# Waschmaschine\n", listOf(QrKey("wm"))))
        assertEquals("---\nqr: wm\n---\n", QrKeys.withKeys("", listOf(QrKey("wm"))))
        assertEquals("Text", QrKeys.withKeys("Text", emptyList()))
    }

    @Test
    fun `the entry is added to a front matter without one`() {
        assertEquals("---\ntitle: WM\nqr: wm\n---\nText", QrKeys.withKeys("---\ntitle: WM\n---\nText", listOf(QrKey("wm"))))
    }

    @Test
    fun `the entry is replaced in every form, and the rest stays`() {
        val text = "---\ntitle: WM\nqr:\n  - key: alt\n    label: x\n- weiter\n\ntags: [a]\n---\nText"
        assertEquals(
            "---\ntitle: WM\nqr:\n  - key: wm\n    label: \"Keller, an der \\\"Maschine\\\"\"\n  - \"true\"\n\ntags: [a]\n---\nText",
            QrKeys.withKeys(text, listOf(QrKey("wm", "Keller, an der \"Maschine\""), QrKey("true"))),
        )
        assertEquals("---\nqr: \"7\"\ntags: [x]\n---\n", QrKeys.withKeys("---\nqr: [a, b]\ntags: [x]\n---\n", listOf(QrKey("7"))))
        assertEquals("---\ntags: [x]\n---\n", QrKeys.withKeys("---\nqr: a\ntags: [x]\n---\n", emptyList()))
        assertEquals("---\nqr: [a, b]\n---\n", QrKeys.withKeys("---\nqr: a\n---\n", listOf(QrKey("a"), QrKey("b"))))
    }

    @Test
    fun `what is written is read back`() {
        val keys = listOf(QrKey("wm", "Keller: links"), QrKey("no"), QrKey("0123"))
        assertEquals(keys, QrKeys.keysOf(QrKeys.withKeys("# Seite\n", keys)))
    }

    @Test
    fun `keys are read with windows line ends`() {
        assertEquals(
            listOf(QrKey("wm", "Keller"), QrKey("tr")),
            QrKeys.keysOf("---\r\nqr:\r\n  - key: wm\r\n    label: Keller\r\n  - tr\r\n---\r\nText"),
        )
    }
}
