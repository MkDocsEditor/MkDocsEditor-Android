package de.markusressel.mkdocseditor.feature.browser.ui

import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity

internal sealed class FileBrowserEvent {
    object ReloadEvent : FileBrowserEvent()
    data class CreateSectionEvent(val parentId: String) : FileBrowserEvent()
    data class CreateDocumentEvent(val parentId: String) : FileBrowserEvent()
    data class RenameDocumentEvent(val entity: DocumentEntity) : FileBrowserEvent()
}