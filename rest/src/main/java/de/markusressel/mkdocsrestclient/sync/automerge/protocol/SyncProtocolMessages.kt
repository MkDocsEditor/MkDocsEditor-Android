package de.markusressel.mkdocsrestclient.sync.automerge.protocol

import com.squareup.moshi.JsonClass

/*
 * Messages of the document websocket, version 1.
 * See docs/sync-protocol.md in MkDocsEditor-Backend.
 * Binary Automerge data is standard base64 (with padding) inside the JSON.
 */

object MessageType {
    const val HELLO = "hello"
    const val INITIAL_CONTENT = "initial-content"
    const val SYNC_REQUEST = "sync-request"
    const val PRESENCE = "presence"
    const val PRESENCE_SNAPSHOT = "presence-snapshot"
    const val PRESENCE_LEAVE = "presence-leave"
    const val ACTOR = "actor"
    const val HEARTBEAT = "heartbeat"
    const val DOCUMENT_RENAMED = "document-renamed"
    const val DOCUMENT_DELETED = "document-deleted"
    const val ERROR = "error"
}

/**
 * Only used to read the type of an incoming message
 */
@JsonClass(generateAdapter = true)
data class MessageEnvelope(
    val type: String,
)

@JsonClass(generateAdapter = true)
data class HelloMessage(
    val documentId: String,
    val actorId: String,
    val epoch: String? = null,
    val ticket: String? = null,
    val client: String? = null,
    val type: String = MessageType.HELLO,
)

@JsonClass(generateAdapter = true)
data class SyncRequestMessage(
    val documentId: String,
    val syncMessage: String,
    val type: String = MessageType.SYNC_REQUEST,
)

@JsonClass(generateAdapter = true)
data class HeartbeatMessage(
    val type: String = MessageType.HEARTBEAT,
)

@JsonClass(generateAdapter = true)
data class SelectionMessage(
    val anchor: String,
    val head: String,
)

/**
 * Presence, in both directions. The server fills in (and overwrites) the
 * fields that identify the connection.
 */
@JsonClass(generateAdapter = true)
data class PresenceMessage(
    val documentId: String,
    val selections: List<SelectionMessage> = emptyList(),
    val clientId: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val color: String? = null,
    val actorId: String? = null,
    val type: String = MessageType.PRESENCE,
)

@JsonClass(generateAdapter = true)
data class PresenceSnapshotMessage(
    val documentId: String,
    val peers: List<PresenceMessage> = emptyList(),
    val type: String = MessageType.PRESENCE_SNAPSHOT,
)

@JsonClass(generateAdapter = true)
data class PresenceLeaveMessage(
    val documentId: String,
    val clientId: String,
    val type: String = MessageType.PRESENCE_LEAVE,
)

@JsonClass(generateAdapter = true)
data class UserInfo(
    val username: String,
    val clientId: String? = null,
    val displayName: String? = null,
    val role: String? = null,
    val color: String? = null,
)

@JsonClass(generateAdapter = true)
data class ActorInfo(
    val actorId: String,
    val username: String,
    val displayName: String? = null,
    val color: String? = null,
)

@JsonClass(generateAdapter = true)
data class ActorMessage(
    val actorId: String,
    val username: String,
    val displayName: String? = null,
    val color: String? = null,
    val type: String = MessageType.ACTOR,
)

@JsonClass(generateAdapter = true)
data class InitialContentMessage(
    val documentId: String,
    val epoch: String,
    val documentState: String,
    val content: String? = null,
    val readOnly: Boolean = false,
    val you: UserInfo? = null,
    val actors: List<ActorInfo> = emptyList(),
    val type: String = MessageType.INITIAL_CONTENT,
)

@JsonClass(generateAdapter = true)
data class DocumentRenamedMessage(
    val documentId: String,
    val newDocumentId: String,
    val type: String = MessageType.DOCUMENT_RENAMED,
)

@JsonClass(generateAdapter = true)
data class DocumentDeletedMessage(
    val documentId: String,
    val type: String = MessageType.DOCUMENT_DELETED,
)

@JsonClass(generateAdapter = true)
data class ErrorMessage(
    val code: String,
    val message: String? = null,
    val type: String = MessageType.ERROR,
)

/**
 * Websocket close codes of the protocol
 */
object CloseCode {
    const val NORMAL = 1000
    const val GOING_AWAY = 1001
    const val UNAUTHORIZED = 4001
    const val FORBIDDEN = 4003
    const val NOT_FOUND = 4004
    const val HEARTBEAT_TIMEOUT = 4008
    const val RENAMED = 4009
    const val PROTOCOL_ERROR = 4400
}
