package de.markusressel.mkdocsrestclient.sync.automerge

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import org.automerge.Document

/**
 * Entity holding information about an EditRequest made by this or other clients
 */
@JsonClass(generateAdapter = true)
data class SyncRequestEntity(
    val type: String = "sync-request",
    @Json(name = "requestId")
    val requestId: String,
    @Json(name = "documentId")
    val documentId: String,
    @Json(name = "documentState")
    val document: Document,
    @Json(name = "syncMessage")
    val syncMessageWrapper: SyncMessageWrapper,
)