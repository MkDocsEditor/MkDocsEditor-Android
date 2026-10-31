package de.markusressel.mkdocseditor.extensions.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringExtensionsTest {

    @Test
    fun `safeSubstring clamps out of range indices`() {
        assertEquals("abc", "abc".safeSubstring(-5, 10))
        assertEquals("bc", "abc".safeSubstring(1))
        assertEquals("", "abc".safeSubstring(7))
    }

    @Test
    fun `startsWithAny matches any prefix`() {
        assertTrue("# Title".startsWithAny("## ", "# "))
        assertFalse("Title".startsWithAny("## ", "# "))
    }
}
