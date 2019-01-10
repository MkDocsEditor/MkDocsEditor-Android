package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import io.objectbox.kotlin.query
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentContentPersistenceManager
@Inject constructor(
        private val documentPersistenceManager: DocumentPersistenceManager)
    : PersistenceManagerBase<DocumentContentEntity>(DocumentContentEntity::class) {

    /**
     * Updates or creates a DocumentContentEntity with the given data and
     * saves it in the database.
     *
     * @param documentId the documentId for reference
     * @param text the text content of the document
     */
    fun insertOrUpdate(documentId: String, text: String? = null,
                       selection: Int? = null,
                       zoomLevel: Float? = null,
                       panX: Float? = null,
                       panY: Float? = null) {
        val entity = standardOperation().query {
            equal(DocumentContentEntity_.documentId, documentId)
        }.findUnique()
                ?: DocumentContentEntity(entityId = 0,
                        date = System.currentTimeMillis(),
                        documentId = documentId)

        // attach parent if necessary
        if (entity.documentEntity.isNull) {
            val documentEntity = documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, documentId)
            }.findUnique()
            entity.documentEntity.target = documentEntity
        }

        // set values if present
        entity.date = System.currentTimeMillis()
        text?.let { entity.text = it }
        selection?.let { entity.selection = it }
        zoomLevel?.let { entity.zoomLevel = it }
        panX?.let { entity.panX = it }
        panY?.let { entity.panY = it }

        standardOperation().put(entity)
    }
}