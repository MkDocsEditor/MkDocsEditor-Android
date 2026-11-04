package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.base.WikiDatabase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentPersistenceManager @Inject constructor(
    database: WikiDatabase,
) : PersistenceManagerBase<DocumentEntity>(database, DocumentEntity::class) {

    fun findById(documentId: String): DocumentEntity? = findUnique(byId(documentId))

    fun observeById(documentId: String): Flow<DocumentEntity?> = observeFirst(byId(documentId))

    private fun byId(documentId: String) = DocumentEntity_.id.equal(documentId, StringOrder.CASE_SENSITIVE)
}
