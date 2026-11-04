package de.markusressel.mkdocseditor.feature.filebrowser.data

data class SectionData(
    val entityId: Long,
    val id: String,
    val name: String,
    val subsections: List<SectionData>,
    val documents: List<DocumentData>,
    val resources: List<ResourceData>
)

/** when something in this folder or below it last changed, null if it holds nothing with a date */
internal fun SectionData.lastChange(): java.util.Date? =
    (documents.map { it.modtime } + resources.map { it.modtime } + subsections.mapNotNull { it.lastChange() }).maxOrNull()
