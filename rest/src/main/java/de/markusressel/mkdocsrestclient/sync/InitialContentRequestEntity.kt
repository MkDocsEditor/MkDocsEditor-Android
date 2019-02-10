package de.markusressel.mkdocsrestclient.sync

/**
 * Entity holding information about the initial state of a document
 */
data class InitialContentRequestEntity(val type: String,
                                       val requestId: String,
                                       val documentId: String,
                                       val content: String)