package de.markusressel.mkdocsrestclient.sync.websocket

import com.squareup.moshi.JsonClass

/**
 * Used to identify what this package should be used for
 */
@JsonClass(generateAdapter = true)
data class SocketEntityBase(
    val type: String,
    val requestId: String,
    val documentId: String
)