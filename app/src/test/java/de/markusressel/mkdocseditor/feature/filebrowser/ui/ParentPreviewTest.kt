package de.markusressel.mkdocseditor.feature.filebrowser.ui

import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.fractionWhileReturning
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ParentPreviewTest {

    private val path = listOf(SectionItem("root", "/"), SectionItem("a", "a"), SectionItem("b", "b"))

    @Test
    fun `the back gesture shows the stored parent of the open folder`() {
        val parent = FolderContent(sectionId = "a")
        assertEquals(parent, UiState(currentSectionPath = path, parentFolderContent = parent).parentPreview)
    }

    @Test
    fun `the parent of the folder that was open before does not show`() {
        val former = FolderContent(sectionId = "root")
        assertNull(UiState(currentSectionPath = path, parentFolderContent = former).parentPreview)
    }

    @Test
    fun `the top-level folder has nothing to show`() {
        val stale = FolderContent(sectionId = "root")
        assertNull(UiState(currentSectionPath = path.take(1), parentFolderContent = stale).parentPreview)
    }

    @Test
    fun `a cancelled gesture returns at the speed of the folder animation`() {
        assertEquals(0.5F, fractionWhileReturning(start = 0.5F, elapsedMs = 0))
        assertEquals(0.3F, fractionWhileReturning(start = 0.5F, elapsedMs = 50), 0.0001F)
        assertEquals(0F, fractionWhileReturning(start = 0.5F, elapsedMs = 125))
        assertEquals(0F, fractionWhileReturning(start = 0.5F, elapsedMs = 1000))
    }
}
