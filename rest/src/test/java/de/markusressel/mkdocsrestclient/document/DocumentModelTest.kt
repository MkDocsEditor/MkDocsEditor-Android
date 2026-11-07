package de.markusressel.mkdocsrestclient.document

import de.markusressel.mkdocsrestclient.section.SectionModel
import de.markusressel.mkdocsrestclient.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DocumentModelTest {

    @Test
    fun `a document of a backend with page-meta has its title and meta`() {
        val document = """
            {"type": "document", "id": "d1", "name": "setup", "filesize": 42, "modtime": "2026-11-06T10:00:00Z",
             "url": "/document/d1/", "path": "guide/setup.md", "title": "Getting started",
             "meta": {"title": "Getting started", "description": "First steps", "icon": "material/rocket",
                      "status": "new", "tags": ["setup", "intro"], "searchExclude": true}}
        """.trimIndent().toEntity<DocumentModel>()

        assertEquals("Getting started", document.title)
        assertEquals(
            PageMetaModel(
                title = "Getting started",
                description = "First steps",
                icon = "material/rocket",
                status = "new",
                tags = listOf("setup", "intro"),
                searchExclude = true,
            ),
            document.meta,
        )
    }

    @Test
    fun `a page without front matter has an empty meta`() {
        val document = """
            {"type": "document", "id": "d1", "name": "setup", "filesize": 42, "modtime": "2026-11-06T10:00:00Z",
             "url": "/document/d1/", "title": "Setup",
             "meta": {"title": null, "description": null, "icon": null, "status": null, "tags": [], "searchExclude": false}}
        """.trimIndent().toEntity<DocumentModel>()

        assertEquals("Setup", document.title)
        assertEquals(PageMetaModel(), document.meta)
    }

    @Test
    fun `the tree of an older backend without page-meta is still read`() {
        val tree = """
            {"id": "root", "name": "docs", "subsections": [], "resources": [], "documents": [
              {"type": "document", "id": "d1", "name": "setup", "filesize": 42, "modtime": "2026-11-06T10:00:00Z",
               "url": "/document/d1/"}
            ]}
        """.trimIndent().toEntity<SectionModel>()

        val document = tree.documents.single()
        assertEquals("setup", document.name)
        assertNull(document.title)
        assertNull(document.meta)
    }
}
