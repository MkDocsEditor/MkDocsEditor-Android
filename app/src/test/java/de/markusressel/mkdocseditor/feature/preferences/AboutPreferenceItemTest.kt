package de.markusressel.mkdocseditor.feature.preferences

import de.markusressel.mkdocseditor.feature.preferences.domain.AboutPreferenceItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AboutPreferenceItemTest {

    @Test
    fun `the card of the about entry shows its text and opens the about page`() {
        var opened = 0
        val item = AboutPreferenceItem(key = "about", icon = null, title = "About", description = "1.2.3", onClick = { opened++ })

        val card = item.asCategoryCard()
        assertEquals("about", card.key)
        assertEquals("About", card.title)
        assertEquals("1.2.3", card.description)
        // a click opens the page instead of an empty category
        card.onClick?.invoke()
        assertEquals(1, opened)
        assertTrue(card.children.isEmpty())
    }
}
