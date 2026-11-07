package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.LeavingChip
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.chipAlpha
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.fractionTowards
import kotlin.test.Test
import kotlin.test.assertEquals

class LeavingChipTest {

    @Test
    fun `the chip of the open folder fades with the back gesture`() {
        assertEquals(1F, chipAlpha("b", LeavingChip("b", progress = 0F)))
        assertEquals(0.75F, chipAlpha("b", LeavingChip("b", progress = 0.25F)))
        assertEquals(0F, chipAlpha("b", LeavingChip("b", progress = 1F)))
    }

    @Test
    fun `the other chips stay while the back gesture goes`() {
        assertEquals(1F, chipAlpha("a", LeavingChip("b", progress = 0.5F)))
        assertEquals(1F, chipAlpha("a", leaving = null))
    }

    @Test
    fun `a gesture beyond its ends does not make a chip more than shown or less than gone`() {
        assertEquals(1F, chipAlpha("b", LeavingChip("b", progress = -0.5F)))
        assertEquals(0F, chipAlpha("b", LeavingChip("b", progress = 1.5F)))
    }

    @Test
    fun `a released gesture finishes the rest of the way at the speed of the folder animation`() {
        assertEquals(0.6F, fractionTowards(start = 0.6F, target = 1F, elapsedMs = 0))
        assertEquals(0.8F, fractionTowards(start = 0.6F, target = 1F, elapsedMs = 50), 0.0001F)
        assertEquals(1F, fractionTowards(start = 0.6F, target = 1F, elapsedMs = 100))
        assertEquals(1F, fractionTowards(start = 0.6F, target = 1F, elapsedMs = 1000))
    }
}
