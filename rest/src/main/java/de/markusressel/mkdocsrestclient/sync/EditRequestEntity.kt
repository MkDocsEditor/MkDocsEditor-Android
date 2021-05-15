package de.markusressel.mkdocsrestclient.sync

import com.squareup.moshi.JsonClass

/**
 * Entity holding information about an EditRequest made by this or other clients
 */
@JsonClass(generateAdapter = true)
data class EditRequestEntity(
    val type: String = "edit-request",
    val requestId: String,
    val documentId: String,
    val patches: String,
    val shadowChecksum: String
)