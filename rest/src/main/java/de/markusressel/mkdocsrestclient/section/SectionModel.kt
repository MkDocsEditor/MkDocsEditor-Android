package de.markusressel.mkdocsrestclient.section

import com.squareup.moshi.JsonClass
import de.markusressel.mkdocsrestclient.document.DocumentModel
import de.markusressel.mkdocsrestclient.resource.ResourceModel

/**
 * Created by Markus on 03.06.2018.
 */
@JsonClass(generateAdapter = true)
data class SectionModel(
    val id: String,
    val name: String,
    val subsections: List<SectionModel>,
    val documents: List<DocumentModel>,
    val resources: List<ResourceModel>
)