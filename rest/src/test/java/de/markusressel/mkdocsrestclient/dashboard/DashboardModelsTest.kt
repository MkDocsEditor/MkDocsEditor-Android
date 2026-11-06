package de.markusressel.mkdocsrestclient.dashboard

import de.markusressel.mkdocsrestclient.toEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DashboardModelsTest {

    @Test
    fun `a feed page of the backend is read with all its fields`() {
        val page = """
            {"entries": [
              {"id": "e1", "time": "2026-11-06T10:00:00Z", "kind": "edited", "itemType": "document", "documentId": "d1",
               "path": "guide/setup.md", "authors": [
                 {"accountId": "a1", "username": "anna", "displayName": "Anna", "color": "#1971c2", "avatar": "0123456789abcdef"},
                 {"displayName": "External change", "color": "#868e96", "avatar": null}
               ], "summary": {"linesAdded": 3, "linesRemoved": 1, "headings": ["Setup"]}},
              {"id": "e2", "time": "2026-11-06T09:00:00Z", "kind": "moved", "itemType": "section",
               "path": "b/guide", "oldPath": "guide", "authors": []}
            ], "next": "c2"}
        """.trimIndent().toEntity<FeedPageModel>()

        assertEquals("c2", page.next)
        val edited = page.entries[0]
        assertEquals(FeedKind.EDITED, edited.kind)
        assertEquals(listOf("Setup"), edited.summary?.headings)
        assertNull(edited.authors[1].accountId)
        val moved = page.entries[1]
        assertNull(moved.documentId)
        assertEquals("guide", moved.oldPath)
        assertNull(moved.summary)
    }

    @Test
    fun `a stored dashboard reads back the same`() {
        val pins = """
            {"family": [{"documentId": "d1", "path": "index.md", "name": "index", "pinnedAt": "2026-11-06T10:00:00Z",
              "pinnedBy": {"accountId": "a1", "username": "anna", "displayName": "Anna", "color": "#1971c2"}}],
             "personal": []}
        """.trimIndent().toEntity<PinsModel>()
        val snapshot = DashboardSnapshotModel(pins = pins, feed = FeedPageModel(next = "c1"))

        assertEquals(snapshot, DashboardSnapshotModel.parse(snapshot.toJson()))
        assertNull(DashboardSnapshotModel.parse("not json"))
    }
}
