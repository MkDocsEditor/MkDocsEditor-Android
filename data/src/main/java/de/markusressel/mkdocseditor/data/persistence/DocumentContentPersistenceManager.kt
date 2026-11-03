package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentContentPersistenceManager @Inject constructor(
    boxStore: BoxStore,
    private val documentPersistenceManager: DocumentPersistenceManager,
) : PersistenceManagerBase<DocumentContentEntity>(boxStore, DocumentContentEntity::class) {

    fun findByDocumentId(documentId: String): DocumentContentEntity? =
        findUnique(DocumentContentEntity_.documentId.equal(documentId, StringOrder.CASE_SENSITIVE))

    /**
     * Updates or creates a DocumentContentEntity with the given data and saves it in the database. All in one
     * transaction: the background sync and the editor write to the same row, and neither may undo the other.
     *
     * @param documentId the documentId for reference
     * @param text the text content of the document
     */
    fun insertOrUpdate(
        documentId: String,
        text: String? = null,
        selection: Int? = null,
        zoomLevel: Float? = null,
        panX: Float? = null,
        panY: Float? = null
    ) {
        boxStore.runInTx {
            val entity = findByDocumentId(documentId) ?: DocumentContentEntity(documentId = documentId)

            entity.date = System.currentTimeMillis()
            text?.let { entity.text = it }
            selection?.let { entity.selection = it }
            zoomLevel?.let { entity.zoomLevel = it }
            panX?.let { entity.panX = it }
            panY?.let { entity.panY = it }

            val document = documentPersistenceManager.findById(documentId)
            entity.documentEntity.target = document
            standardOperation().put(entity)

            // every observer of documents hears about this, so only when it changes something
            if (document != null && document.content.targetId != entity.entityId) {
                document.content.target = entity
                documentPersistenceManager.standardOperation().put(document)
            }
        }
    }

    /** Removes the cached content of all documents other than the given ones */
    fun deleteAllExcept(documentIds: Collection<String>) {
        val kept = findIds(
            DocumentContentEntity_.documentId.oneOf(documentIds.toTypedArray(), StringOrder.CASE_SENSITIVE)
        )
        standardOperation().removeByIds(findIds(DocumentContentEntity_.entityId.notOneOf(kept)).toList())
    }
}
