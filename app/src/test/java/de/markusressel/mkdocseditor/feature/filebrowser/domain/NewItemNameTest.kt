package de.markusressel.mkdocseditor.feature.filebrowser.domain

import de.markusressel.mkdocseditor.feature.editor.documentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.toFolderContent
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewItemNameTest {

    private fun section(name: String) = SectionData(0, "s-$name", name, emptyList(), emptyList(), emptyList())
    private fun resource(name: String) = ResourceData(0, "r-$name", name, 1, Date(), false)

    private val folder = FolderContent(
        sections = listOf(section("guide")),
        documents = listOf(documentData("page")),
        resources = listOf(resource("logo.png")),
    )

    @Test
    fun `a new name is fine`() = assertNull(newItemNameProblem("new", folder))

    @Test
    fun `an empty name is not`() {
        assertEquals(NewItemNameProblem.Blank, newItemNameProblem("", folder))
        assertEquals(NewItemNameProblem.Blank, newItemNameProblem("   ", folder))
    }

    @Test
    fun `a name of any kind of item in the folder is taken`() {
        assertEquals(NewItemNameProblem.AlreadyExists, newItemNameProblem("guide", folder))
        assertEquals(NewItemNameProblem.AlreadyExists, newItemNameProblem("page", folder))
        assertEquals(NewItemNameProblem.AlreadyExists, newItemNameProblem("logo.png", folder))
    }

    @Test
    fun `spaces around a name do not make it a new one`() =
        assertEquals(NewItemNameProblem.AlreadyExists, newItemNameProblem(" guide ", folder))

    @Test
    fun `the backend does not allow these`() {
        listOf(".hidden", "a/b", "a\\b", "a\u0000b").forEach {
            assertEquals(NewItemNameProblem.NotAllowed, newItemNameProblem(it, folder), it)
        }
    }

    @Test
    fun `an unknown folder only checks the name itself`() {
        assertNull(newItemNameProblem("guide", null))
        assertEquals(NewItemNameProblem.NotAllowed, newItemNameProblem("a/b", null))
    }

    @Test
    fun `a folder is sorted by name, without regard to case`() {
        val content = SectionData(
            0, "s", "s",
            subsections = listOf(section("b"), section("A")),
            documents = listOf(documentData("z"), documentData("Y")),
            resources = listOf(resource("n"), resource("M")),
        ).toFolderContent()

        assertEquals(listOf("A", "b"), content.sections.map { it.name })
        assertEquals(listOf("Y", "z"), content.documents.map { it.name })
        assertEquals(listOf("M", "n"), content.resources.map { it.name })
    }
}
