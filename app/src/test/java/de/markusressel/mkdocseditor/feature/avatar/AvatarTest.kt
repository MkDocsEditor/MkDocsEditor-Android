package de.markusressel.mkdocseditor.feature.avatar

import de.markusressel.mkdocseditor.auth.api.avatarPath
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
import de.markusressel.mkdocseditor.feature.avatar.ui.compose.initialOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AvatarTest {

    @Test
    fun `an account without a picture or without an id has no avatar`() {
        assertNull(Avatar.of("a1", null))
        assertNull(Avatar.of(null, "0123456789abcdef"))
        assertNull(Avatar.of("", "0123456789abcdef"))
        assertEquals(Avatar("a1", "0123456789abcdef"), Avatar.of("a1", "0123456789abcdef"))
    }

    @Test
    fun `the path of a picture names the account and its version`() {
        assertEquals("avatars/k3x8/0123456789abcdef/", avatarPath("k3x8", "0123456789abcdef"))
        assertEquals("avatars/a%2Fb/v/", avatarPath("a/b", "v"))
    }

    @Test
    fun `the initial keeps an emoji whole`() {
        assertEquals("A", initialOf(" anna"))
        assertEquals("😀", initialOf("😀 Smiley"))
        assertEquals("", initialOf(""))
    }
}
