package de.markusressel.mkdocseditor.feature.browser.data

data class SectionData(
    val entityId: Long,
    val id: String,
    val name: String,
    val subsections: List<SectionData>,
    val documents: List<DocumentData>,
    val resources: List<ResourceData>
)