package de.markusressel.mkdocseditor.data.persistence

import de.markusressel.mkdocseditor.data.persistence.base.PersistenceManagerBase
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
        private val resourcePersistenceManager: ResourcePersistenceManager
) : PersistenceManagerBase<SectionEntity>(SectionEntity::class) {

    /**
     * Inserts sections (and their contents) if missing,
     * updates any already existing entities,
     * and finally removes entities that are not present in the given dataset
     */
    fun insertOrUpdateRoot(rootSection: SectionEntity) {
        addOrUpdate(rootSection)

        // remove data that is not on the server anymore
        deleteMissing(rootSection)
    }


    private fun addOrUpdate(rootSection: SectionEntity) {
        var rootEntity = standardOperation().query {
            equal(SectionEntity_.id, "root")
        }.findUnique()

        if (rootEntity != null) {
            addOrUpdateEntityFields(rootEntity, rootSection)
        } else {
            rootEntity = rootSection
        }

        standardOperation().put(rootEntity)
    }

    private fun addOrUpdateEntityFields(existingData: SectionEntity, newData: SectionEntity) {
        // TODO: do we have to update the section itself? Currently I don't think so.

        newData.documents.forEach { newDocument ->
            val existingDocument = existingData.documents.firstOrNull { it.id == newDocument.id }

            if (existingDocument != null) {
                existingDocument.filesize = newDocument.filesize
                existingDocument.modtime = newDocument.modtime
            } else {
                documentPersistenceManager.standardOperation().put(newDocument)
            }
        }

        newData.resources.forEach { newResource ->
            val existingResource = existingData.resources.firstOrNull { it.id == newResource.id }

            if (existingResource != null) {
                existingResource.filesize = newResource.filesize
                existingResource.modtime = newResource.modtime
            } else {
                resourcePersistenceManager.standardOperation().put(newResource)
            }
        }

        newData.subsections.forEach { newSection ->
            val existingSection = existingData.subsections.firstOrNull { it.id == newSection.id }

            if (existingSection != null) {
                addOrUpdateEntityFields(existingSection, newSection)
            } else {
                standardOperation().put(newSection)
            }
        }
    }

    private fun deleteMissing(newData: SectionEntity) {
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

        standardOperation().removeByKeys(missingSectionIds.toList())

        // remove stale documents
        val existingDocumentIds = documentPersistenceManager.standardOperation().query {
            `in`(DocumentEntity_.id, documentIds.toTypedArray())
        }.findIds()

        // find others
        val missingDocumentIds = documentPersistenceManager.standardOperation().query {
            notIn(DocumentEntity_.entityId, existingDocumentIds)
        }.findIds()

        documentPersistenceManager.standardOperation().removeByKeys(missingDocumentIds.toList())

        // remove stale resources
        val existingResourceIds = resourcePersistenceManager.standardOperation().query {
            `in`(ResourceEntity_.id, resourceIds.toTypedArray())
        }.findIds()

        // find others
        val missingResourceIds = resourcePersistenceManager.standardOperation().query {
            notIn(ResourceEntity_.entityId, existingResourceIds)
        }.findIds()

        resourcePersistenceManager.standardOperation().removeByKeys(missingResourceIds.toList())
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