package de.markusressel.mkdocseditor.feature.auth.account

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocseditor.feature.auth.account.ui.compose.toHex
import de.markusressel.mkdocseditor.feature.editor.ui.compose.parsePeerColor
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileColorTest {

    @Test
    fun `a picked color is written the way the server takes it`() {
        assertEquals("#3f51b5", Color(0xFF3F51B5).toHex())
        assertEquals("#000000", Color.Black.toHex())
        // the alpha is dropped
        assertEquals("#ff0000", Color.Red.copy(alpha = 0.5f).toHex())
    }

    @Test
    fun `a typed color goes back to the same text`() {
        assertEquals("#e8590c", parsePeerColor("#e8590c")?.toHex())
    }
}
