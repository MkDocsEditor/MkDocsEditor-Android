package de.markusressel.mkdocsrestclient.sync

import com.squareup.moshi.JsonClass

/**
 * Entity holding information about the initial state of a document
 */
@JsonClass(generateAdapter = true)
data class InitialContentRequestEntity(
    val type: String,
    val requestId: String,
    val documentId: String,
    val content: String
)