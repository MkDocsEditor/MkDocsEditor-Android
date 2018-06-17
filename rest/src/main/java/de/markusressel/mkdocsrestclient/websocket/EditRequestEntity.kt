package de.markusressel.mkdocsrestclient.websocket

/**
 * Entity holding information about an EditRequest made by this or other clients
 */
data class EditRequestEntity(
        val id: String,
        val patches: String
)