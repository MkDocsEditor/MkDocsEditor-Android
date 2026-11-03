package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder.StringOrder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionPersistenceManager @Inject constructor(
    boxStore: BoxStore,
    private val documentPersistenceManager: DocumentPersistenceManager,
    private val documentContentPersistenceManager: DocumentContentPersistenceManager,
    private val resourcePersistenceManager: ResourcePersistenceManager,
) : PersistenceManagerBase<SectionEntity>(boxStore, SectionEntity::class) {

    /**
     * Find a section by it's document id (not the database entity id)
     */
    fun findById(sectionId: String): SectionEntity? = findUnique(byId(sectionId))

    /**
     * Find a section by it's document id (not the database entity id)
     */
    fun findByIdFlow(sectionId: String): Flow<SectionEntity?> = observeFirst(byId(sectionId))

    /**
     * Replaces the whole tree with the given one. The cached content of the documents stays, unless a document is not
     * part of the new tree.
     */
    fun insertOrUpdateRoot(newData: SectionEntity) {
        boxStore.runInTx {
            documentPersistenceManager.deleteAll()
            resourcePersistenceManager.deleteAll()
            deleteAll()

            val documentIds = mutableListOf<String>()
            insert(newData, documentIds)
            documentContentPersistenceManager.deleteAllExcept(documentIds)
        }
    }

    private fun insert(section: SectionEntity, documentIds: MutableList<String>) {
        standardOperation().put(section)

        section.documents.forEach { document ->
            documentIds.add(document.id)
            document.parentSection.target = section
            insertWithContent(document)
        }

        section.resources.forEach { resource ->
            resource.parentSection.target = section
            resourcePersistenceManager.standardOperation().put(resource)
        }

        section.subsections.forEach { subsection ->
            subsection.parentSection.target = section
            insert(subsection, documentIds)
        }
    }

    private fun insertWithContent(document: DocumentEntity) {
        val content = documentContentPersistenceManager.findByDocumentId(document.id)
        document.content.target = content
        documentPersistenceManager.standardOperation().put(document)

        if (content != null) {
            content.documentEntity.target = document
            documentContentPersistenceManager.standardOperation().put(content)
        }
    }

    /** Adds a section the server just created to its parent */
    fun addSubsection(parentSection: SectionEntity, section: SectionEntity) {
        section.parentSection.target = parentSection
        parentSection.subsections.add(section)
        standardOperation().put(parentSection)
    }

    /**
     * Deletes the section with the given [sectionId] from the persistence.
     *
     * @param sectionId ID of the section
     */
    fun delete(sectionId: String) {
        standardOperation().removeByIds(findIds(byId(sectionId)).toList())
    }

    /** Removes the whole tree and all cached content */
    fun deleteTreeAndContent() {
        boxStore.runInTx {
            documentContentPersistenceManager.deleteAll()
            resourcePersistenceManager.deleteAll()
            documentPersistenceManager.deleteAll()
            deleteAll()
        }
    }

    private fun byId(sectionId: String) = SectionEntity_.id.equal(sectionId, StringOrder.CASE_SENSITIVE)
}
