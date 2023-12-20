package de.markusressel.mkdocseditor.feature.browser.ui

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity

internal sealed class FileBrowserEvent {
    data class ErrorEvent(val message: String) : FileBrowserEvent()

    data class OpenDocumentEditorEvent(val entity: DocumentEntity) : FileBrowserEvent()
    data class DownloadResourceEvent(val entity: ResourceEntity) : FileBrowserEvent()
    data class CreateSectionEvent(val parentId: String) : FileBrowserEvent()
    data class CreateDocumentEvent(val parentId: String) : FileBrowserEvent()
    data class RenameDocumentEvent(val entity: DocumentEntity) : FileBrowserEvent()
}