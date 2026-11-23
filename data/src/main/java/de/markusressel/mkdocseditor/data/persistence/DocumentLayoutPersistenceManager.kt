package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.base.WikiDatabase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentLayoutEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentLayoutEntity_
import io.objectbox.query.QueryBuilder.StringOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where the user was in a document: the lines at the top of the editor and of the page, the selection, and a line of
 * the text to find that place again. Lines and columns, not offsets, see [DocumentLayoutEntity].
 */
data class SavedDocumentView(
    val editorTopLine: Double?,
    val pageTopLine: Double?,
    val selectionStartLine: Int,
    val selectionStartColumn: Int,
    val selectionEndLine: Int,
    val selectionEndColumn: Int,
    val anchorLine: Int,
    val anchorText: String?,
)

@Singleton
class DocumentLayoutPersistenceManager @Inject constructor(
    database: WikiDatabase,
) : PersistenceManagerBase<DocumentLayoutEntity>(database, DocumentLayoutEntity::class) {

    /** @return the saved ratio of the document, null if the user never changed it */
    fun getSplitRatio(documentId: String): Float? = find(documentId)?.splitRatio?.takeIf { it > 0f }

    fun saveSplitRatio(documentId: String, ratio: Float) {
        boxStore.runInTx {
            val entity = find(documentId) ?: DocumentLayoutEntity(documentId = documentId)
            entity.splitRatio = ratio
            standardOperation().put(entity)
        }
    }

    /** @return where the user was in the document, null if nothing was saved */
    fun getView(documentId: String): SavedDocumentView? = find(documentId)?.takeIf { it.viewSavedAt > 0 }?.let {
        SavedDocumentView(
            editorTopLine = it.editorTopLine,
            pageTopLine = it.pageTopLine,
            selectionStartLine = it.selectionStartLine,
            selectionStartColumn = it.selectionStartColumn,
            selectionEndLine = it.selectionEndLine,
            selectionEndColumn = it.selectionEndColumn,
            anchorLine = it.anchorLine,
            anchorText = it.anchorText,
        )
    }

    /** Saves the view of a document, and forgets the views of all but the [MAX_VIEWS] newest */
    fun saveView(documentId: String, view: SavedDocumentView, now: Long = System.currentTimeMillis()) {
        boxStore.runInTx {
            val entity = find(documentId) ?: DocumentLayoutEntity(documentId = documentId)
            entity.viewSavedAt = now
            entity.editorTopLine = view.editorTopLine
            entity.pageTopLine = view.pageTopLine
            entity.selectionStartLine = view.selectionStartLine
            entity.selectionStartColumn = view.selectionStartColumn
            entity.selectionEndLine = view.selectionEndLine
            entity.selectionEndColumn = view.selectionEndColumn
            entity.anchorLine = view.anchorLine
            entity.anchorText = view.anchorText
            standardOperation().put(entity)

            val old = standardOperation().query(DocumentLayoutEntity_.viewSavedAt.greater(0))
                .orderDesc(DocumentLayoutEntity_.viewSavedAt)
                .build()
                .use { it.find(MAX_VIEWS.toLong(), Long.MAX_VALUE) }
            old.forEach { forgetView(it) }
        }
    }

    /** Forgets everything about documents that are not in the wiki anymore */
    fun deleteAllExcept(documentIds: Collection<String>) {
        val kept = findIds(
            DocumentLayoutEntity_.documentId.oneOf(documentIds.toTypedArray(), StringOrder.CASE_SENSITIVE)
        )
        standardOperation().removeByIds(findIds(DocumentLayoutEntity_.entityId.notOneOf(kept)).toList())
    }

    /** the split ratio stays, it is small and the user chose it */
    private fun forgetView(entity: DocumentLayoutEntity) {
        if (entity.splitRatio > 0f) {
            standardOperation().put(
                entity.copy(viewSavedAt = 0, editorTopLine = null, pageTopLine = null, anchorText = null)
            )
        } else {
            standardOperation().remove(entity)
        }
    }

    private fun find(documentId: String): DocumentLayoutEntity? =
        findUnique(DocumentLayoutEntity_.documentId.equal(documentId, StringOrder.CASE_SENSITIVE))

    companion object {
        /** how many documents keep their view */
        const val MAX_VIEWS = 100
    }
}
