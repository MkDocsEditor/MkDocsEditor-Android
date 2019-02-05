package de.markusressel.mkdocsrestclient.websocket

/**
 * Entity holding information about the initial state of a document
 */
data class InitialContentRequestEntity(val type: String,
                                       val requestId: String,
                                       val documentId: String,
                                       val content: String)