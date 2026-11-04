package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.base.WikiDatabase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentLayoutEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentLayoutEntity_
import io.objectbox.query.QueryBuilder.StringOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentLayoutPersistenceManager @Inject constructor(
    database: WikiDatabase,
) : PersistenceManagerBase<DocumentLayoutEntity>(database, DocumentLayoutEntity::class) {

    /** @return the saved ratio of the document, null if the user never changed it */
    fun getSplitRatio(documentId: String): Float? = find(documentId)?.splitRatio

    fun saveSplitRatio(documentId: String, ratio: Float) {
        val entity = find(documentId) ?: DocumentLayoutEntity(documentId = documentId)
        entity.splitRatio = ratio
        standardOperation().put(entity)
    }

    private fun find(documentId: String): DocumentLayoutEntity? =
        findUnique(DocumentLayoutEntity_.documentId.equal(documentId, StringOrder.CASE_SENSITIVE))
}
