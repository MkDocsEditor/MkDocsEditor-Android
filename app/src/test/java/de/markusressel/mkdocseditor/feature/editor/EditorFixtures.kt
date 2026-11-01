package de.markusressel.mkdocseditor.feature.editor

import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import java.util.Date

internal fun documentData(id: String, name: String = id) =
    DocumentData(0, id, name, 0, Date(0), "", null, false)

internal fun resourceData(id: String, name: String = id) =
    ResourceData(0, id, name, 0, Date(0), false)

internal fun sectionData(
    id: String,
    name: String = id,
    subsections: List<SectionData> = emptyList(),
    documents: List<DocumentData> = emptyList(),
    resources: List<ResourceData> = emptyList(),
) = SectionData(0, id, name, subsections, documents, resources)
