package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity_
import io.objectbox.kotlin.query
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SectionPersistenceManager @Inject constructor(
        private val documentPersistenceManager: DocumentPersistenceManager,
        private val documentContentPersistenceManager: DocumentContentPersistenceManager,
        private val resourcePersistenceManager: ResourcePersistenceManager
) : PersistenceManagerBase<SectionEntity>(SectionEntity::class) {

    /**
     * Get the root section
     */
    fun getRootSection(): SectionEntity? {
        return findById("root")
    }

    /**
     * Find a section by it's document id (not the database entity id)
     */
    fun findById(sectionId: String): SectionEntity? {
        return standardOperation().query {
            equal(SectionEntity_.id, sectionId)
        }.findUnique()
    }

    /**
     * Inserts sections (and their contents) if missing,
     * updates any already existing entities,
     * and finally removes entities that are not present in the given dataset
     */
    fun insertOrUpdateRoot(newData: SectionEntity) {
        boxStore.runInTx {
            addOrUpdate(newData)
            // remove data that is not on the server anymore
            deleteMissing(newData)
        }
    }


    private fun addOrUpdate(rootSection: SectionEntity) {
        addOrUpdateEntityFields(section = rootSection)
    }

    private fun addOrUpdateEntityFields(section: SectionEntity, parentSection: SectionEntity? = null): SectionEntity {
        // use existing section or insert new one
        val sectionEntity = standardOperation().query {
            equal(SectionEntity_.id, section.id)
        }.findUnique() ?: {
            val newSection = standardOperation().get(standardOperation().put(section))
            parentSection?.subsections?.add(newSection)
            newSection
        }()

        section.documents.forEach { newDocument ->
            val documentEntity = documentPersistenceManager.standardOperation().query {
                equal(DocumentEntity_.id, newDocument.id)
            }.findUnique()?.apply {
                filesize = newDocument.filesize
                modtime = newDocument.modtime
            } ?: newDocument
            documentEntity.parentSection.target = sectionEntity

            val documentContentEntity = documentContentPersistenceManager.standardOperation().query {
                equal(DocumentContentEntity_.documentId, documentEntity.id)
            }.findUnique()
            documentEntity.content.target = documentContentEntity

            documentPersistenceManager.standardOperation().put(documentEntity)
        }

        section.resources.forEach { newResource ->
            val resourceEntity = resourcePersistenceManager.standardOperation().query {
                equal(ResourceEntity_.id, newResource.id)
            }.findUnique()?.apply {
                filesize = newResource.filesize
                modtime = newResource.modtime
            } ?: newResource
            resourceEntity.parentSection.target = sectionEntity

            resourcePersistenceManager.standardOperation().put(resourceEntity)
        }

        section.subsections.forEach { subsection ->
            addOrUpdateEntityFields(subsection, sectionEntity)
        }
        // insert updated section
        standardOperation().put(sectionEntity)

        return sectionEntity
    }

    private fun deleteMissing(newData: SectionEntity) {
        // TODO: this currently only works when the whole dataset is updated,
        //  but it should also be possible to update only parts of the tree

        val sectionIds = mutableSetOf<String>()
        val documentIds = mutableSetOf<String>()
        val resourceIds = mutableSetOf<String>()
        findIdsRecursive(newData, sectionIds, documentIds, resourceIds)

        // remove stale sections
        val existingSectionIds = standardOperation().query {
            `in`(SectionEntity_.id, sectionIds.toTypedArray())
        }.findIds()

        // find others
        val missingSectionIds = standardOperation().query {
            notIn(SectionEntity_.entityId, existingSectionIds)
        }.findIds()

        standardOperation().removeByIds(missingSectionIds.toList())

        // remove stale documents
        val existingDocumentIds = documentPersistenceManager.standardOperation().query {
            `in`(DocumentEntity_.id, documentIds.toTypedArray())
        }.findIds()

        // find others
        val missingDocumentIds = documentPersistenceManager.standardOperation().query {
            notIn(DocumentEntity_.entityId, existingDocumentIds)
        }.findIds()

        documentPersistenceManager.standardOperation().removeByIds(missingDocumentIds.toList())

        // remove stale resources
        val existingResourceIds = resourcePersistenceManager.standardOperation().query {
            `in`(ResourceEntity_.id, resourceIds.toTypedArray())
        }.findIds()

        // find others
        val missingResourceIds = resourcePersistenceManager.standardOperation().query {
            notIn(ResourceEntity_.entityId, existingResourceIds)
        }.findIds()

        resourcePersistenceManager.standardOperation().removeByIds(missingResourceIds.toList())
    }

    companion object {

        /**
         * Recursive method to find all section, document and resource ids
         */
        private fun findIdsRecursive(section: SectionEntity, sectionIds: MutableSet<String>, documentIds: MutableSet<String>, resourceIds: MutableSet<String>) {
            sectionIds.add(section.id)
            documentIds.addAll(section.documents.map { it.id })
            resourceIds.addAll(section.resources.map { it.id })
            section.subsections.forEach {
                findIdsRecursive(it, sectionIds, documentIds, resourceIds)
            }
        }
    }


}