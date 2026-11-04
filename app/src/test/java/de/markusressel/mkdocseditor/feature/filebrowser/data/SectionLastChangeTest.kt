package de.markusressel.mkdocseditor.feature.filebrowser.data

import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.editor.sectionData
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SectionLastChangeTest {

    private fun document(time: Long) = documentData("d$time").copy(modtime = Date(time))

    @Test
    fun `the newest date counts, also from subfolders`() {
        val section = sectionData(
            "a",
            documents = listOf(document(5)),
            subsections = listOf(sectionData("b", documents = listOf(document(9), document(1)))),
        )
        assertEquals(Date(9), section.lastChange())
    }

    @Test
    fun `an empty folder has no last change`() {
        assertNull(sectionData("a", subsections = listOf(sectionData("b"))).lastChange())
    }
}
