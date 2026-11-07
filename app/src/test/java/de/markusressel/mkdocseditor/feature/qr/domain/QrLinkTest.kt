package de.markusressel.mkdocseditor.feature.qr.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrLinkTest {

    @Test
    fun `the key of a sticker is read from its URL`() {
        assertEquals("waschmaschine", QrLink.parse("https://edit.wiki.markusressel.de/q/waschmaschine"))
        assertEquals("Heizung", QrLink.parse("https://edit.example.com/q/Heizung?x=1"))
        assertEquals("a b", QrLink.parse("https://edit.example.com/q/a%20b"))
    }

    @Test
    fun `other links are no stickers`() {
        assertNull(QrLink.parse("https://edit.example.com/join/doc"))
        assertNull(QrLink.parse("https://edit.example.com/q/"))
        assertNull(QrLink.parse("mkdocseditor://q/x"))
        assertNull(QrLink.parse("not a link"))
    }

    @Test
    fun `the URL of a sticker is the one the web client prints`() {
        assertEquals("https://edit.example.com/q/wm", QrLink.build("https://edit.example.com/", "wm"))
        assertEquals("https://edit.example.com/q/wm", QrLink.build("https://edit.example.com", "wm"))
    }
}
