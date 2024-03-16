package de.markusressel.mkdocseditor.feature.browser.data

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.storage.AppStorageManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataFactory @Inject constructor(
    private val appStorageManager: AppStorageManager,
) {

    fun toSectionData(entity: SectionEntity): SectionData = with(entity) {
        SectionData(
            entityId = entityId,
            id = id,
            name = name,
            subsections = subsections.map(::toSectionData),
            documents = documents.map(::toDocumentData).toList(),
            resources = resources.map(::toResourceData).toList()
        )
    }

    fun toDocumentData(entity: DocumentEntity) = with(entity) {
        DocumentData(
            entityId = entityId,
            id = id,
            name = name,
            filesize = filesize,
            modtime = modtime,
            url = url,
            content = content.target.toDocumentContentData(),
            isOfflineAvailable = content.target != null
        )
    }

    fun toResourceData(entity: ResourceEntity) = with(entity) {
        ResourceData(
            entityId = entityId,
            id = id,
            name = name,
            filesize = filesize,
            modtime = modtime,
            isOfflineAvailable = appStorageManager.exists(id, name),
        )
    }


    private fun DocumentContentEntity?.toDocumentContentData() = when {
        this == null -> null
        else -> DocumentContentData(
            id = entityId,
            date = date,
            text = text,
            selection = selection,
            zoomLevel = zoomLevel,
            panX = panX,
            panY = panY,
        )
    }

}