package de.markusressel.mkdocsrestclient.websocket

/**
 * Used to identify what this package should be used for
 */
data class SocketEntityBase(val type: String,
                            val requestId: String,
                            val documentId: String)