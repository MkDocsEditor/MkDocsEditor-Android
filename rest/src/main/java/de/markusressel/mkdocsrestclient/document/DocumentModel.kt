package de.markusressel.mkdocsrestclient.document

import com.squareup.moshi.JsonClass
import java.util.*

/**
 * Created by Markus on 03.06.2018.
 */
@JsonClass(generateAdapter = true)
data class DocumentModel(
    val type: String,
    val id: String,
    val name: String,
    val filesize: Long,
    val modtime: Date,
    val url: String
)