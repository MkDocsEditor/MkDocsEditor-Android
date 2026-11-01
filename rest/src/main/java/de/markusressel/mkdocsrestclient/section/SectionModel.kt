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
    /** path relative to the docs folder, `""` for the root (backends with references) */
    val path: String? = null,
    /** id of the index document (`index.md`, else `README.md`), or `""` */
    val index: String? = null,
    val subsections: List<SectionModel>,
    val documents: List<DocumentModel>,
    val resources: List<ResourceModel>
)