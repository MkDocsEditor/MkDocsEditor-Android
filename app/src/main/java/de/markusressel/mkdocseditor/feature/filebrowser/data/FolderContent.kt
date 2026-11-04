package de.markusressel.mkdocseditor.feature.filebrowser.data

/**
 * What a folder shows, each kind sorted by name
 *
 * @param sectionId the folder this is the content of
 */
internal data class FolderContent(
    val sectionId: String = "",
    val sections: List<SectionData> = emptyList(),
    val documents: List<DocumentData> = emptyList(),
    val resources: List<ResourceData> = emptyList(),
) {
    val isEmpty: Boolean get() = sections.isEmpty() && documents.isEmpty() && resources.isEmpty()

    fun hasItemNamed(name: String): Boolean =
        sections.any { it.name == name } || documents.any { it.name == name } || resources.any { it.name == name }
}

internal fun SectionData.toFolderContent() = FolderContent(
    sectionId = id,
    sections = subsections.sortedBy { it.name.lowercase() },
    documents = documents.sortedBy { it.name.lowercase() },
    resources = resources.sortedBy { it.name.lowercase() },
)
