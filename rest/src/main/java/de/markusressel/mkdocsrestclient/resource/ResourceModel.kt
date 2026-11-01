package de.markusressel.mkdocsrestclient.resource

import com.squareup.moshi.JsonClass
import java.util.*

/**
 * Created by Markus on 03.06.2018.
 */
@JsonClass(generateAdapter = true)
data class ResourceModel(
    val type: String,
    val id: String,
    val name: String,
    val filesize: Long,
    val modtime: Date,
    /** path relative to the docs folder (backends with references) */
    val path: String? = null,
    /** from the file extension (backends with references) */
    val mimeType: String? = null,
    /** `image`, `video`, `audio`, `pdf`, `text`, `archive` or `other` (backends with references) */
    val kind: String? = null,
)