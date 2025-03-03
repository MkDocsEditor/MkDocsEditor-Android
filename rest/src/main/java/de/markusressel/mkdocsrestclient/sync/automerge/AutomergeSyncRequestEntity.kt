package de.markusressel.mkdocsrestclient.sync.automerge

import com.squareup.moshi.JsonClass

/**
 * Entity holding information about the initial state of a document
 */
@JsonClass(generateAdapter = true)
data class AutomergeSyncRequestEntity(
    val type: String,
    val requestId: String,
    val documentId: String,
    // *automerge.Doc as base64 encoded string
    val documentState: String,
    // *automerge.SyncMessage as base64 encoded string
    val syncMessage: String,
)