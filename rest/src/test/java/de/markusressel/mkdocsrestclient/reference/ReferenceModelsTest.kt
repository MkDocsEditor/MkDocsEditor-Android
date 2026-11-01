package de.markusressel.mkdocsrestclient.reference

import de.markusressel.mkdocsrestclient.section.SectionModel
import de.markusressel.mkdocsrestclient.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Parses answers in the shape of openapi-description.yaml of the backend
 */
class ReferenceModelsTest {

    @Test
    fun `document references`() {
        val result = """
            {"document":{"type":"document","id":"1","path":"guide/setup.md","name":"setup"},
             "references":[
               {"kind":"image","line":3,"column":9,"destination":"img/a%20b.png","anchor":"","status":"ok",
                "target":{"type":"resource","id":"2","path":"guide/img/a b.png","name":"a b.png"}},
               {"kind":"link","line":5,"column":2,"destination":"../gone.md#x","anchor":"x","status":"broken",
                "target":null,"reason":"not-found","path":"gone.md"}]}
        """.toEntity<DocumentReferencesModel>()
        assertEquals("guide/img/a b.png", result.references[0].target?.path)
        assertNull(result.references[1].target)
        assertEquals("not-found", result.references[1].reason)
    }

    @Test
    fun `backlinks`() {
        val result = """
            {"item":{"type":"resource","id":"2","path":"img/logo.png","name":"logo.png"},
             "backlinks":[{"source":{"type":"document","id":"1","path":"index.md","name":"index"},
               "kind":"image","line":1,"column":10,"destination":"img/logo.png","anchor":"",
               "target":{"type":"resource","id":"2","path":"img/logo.png","name":"logo.png"},"internal":false}]}
        """.toEntity<BacklinksModel>()
        assertEquals("index.md", result.backlinks.single().source.path)
    }

    @Test
    fun `move result and moved`() {
        val result = """
            {"dryRun":true,"type":"document","oldId":"1","newId":"3","oldPath":"guide/setup.md","newPath":"setup.md",
             "moved":[{"type":"document","oldId":"1","newId":"3","oldPath":"guide/setup.md","newPath":"setup.md"}],
             "documents":[{"documentId":"1","newDocumentId":"3","path":"guide/setup.md","newPath":"setup.md",
               "edits":[{"line":3,"before":"[home](../index.md)","after":"[home](index.md)"}]}],
             "config":[{"file":"mkdocs.yml","line":7,"before":"  - guide/setup.md","after":"  - setup.md"}]}
        """.toEntity<MoveResultModel>()
        assertEquals("[home](index.md)", result.documents.single().edits.single().after)
        assertNull(result.commit)

        val moved = """
            {"oldId":"1","type":"document","item":{"type":"document","id":"3","path":"setup.md","name":"setup"},
             "movedAt":"2026-11-01T10:00:00Z"}
        """.toEntity<MovedModel>()
        assertEquals("3", moved.item.id)
    }

    @Test
    fun `tree fields are optional`() {
        val old = """{"type":"section","id":"root","name":"docs","subsections":[],"documents":[],"resources":[]}"""
            .toEntity<SectionModel>()
        assertNull(old.path)
        val new = """
            {"type":"section","id":"root","name":"docs","path":"","index":"1","subsections":[],
             "documents":[{"type":"document","id":"1","name":"index","path":"index.md","filesize":1,
               "modtime":"2026-11-01T10:00:00Z","url":""}],
             "resources":[{"type":"resource","id":"2","name":"a.png","path":"a.png","filesize":1,
               "modtime":"2026-11-01T10:00:00Z","mimeType":"image/png","kind":"image"}]}
        """.toEntity<SectionModel>()
        assertEquals("1", new.index)
        assertEquals("image", new.resources.single().kind)
        assertEquals("index.md", new.documents.single().path)
    }
}
