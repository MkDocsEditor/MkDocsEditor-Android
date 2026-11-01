package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.moshi
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.ActorInfo
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.HelloMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.InitialContentMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.MessageEnvelope
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.MessageType
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.PresenceLeaveMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.PresenceMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.PresenceSnapshotMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.SyncRequestMessage
import de.markusressel.mkdocsrestclient.sync.automerge.protocol.UserInfo
import de.markusressel.mkdocsrestclient.sync.text.TextDiff
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.ObjectType
import org.automerge.SyncState
import java.util.Base64

/**
 * A minimal in-memory implementation of the server side of the sync protocol v1,
 * using automerge-java. Messages are queued and delivered with [pump].
 */
class FakeSyncServer(
    initialText: String,
    documentId: String = "doc-1",
    var readOnly: Boolean = false,
) {

    var documentId: String = documentId
        private set

    /** ids the document had before [move] */
    private val oldIds = mutableSetOf<String>()

    var document: AutomergeTextDocument = createDocument(initialText)
        private set
    var epoch: String = "epoch-1"
        private set

    val receivedHellos = mutableListOf<HelloMessage>()
    val boundActors = mutableMapOf<String, String>()

    inner class Connection(
        val clientId: String,
        val username: String,
        val session: DocumentSyncSession,
    ) {
        var syncState: SyncState? = null
        var presence: PresenceMessage? = null
        var open = true
    }

    private val connections = mutableListOf<Connection>()
    private val toServer = ArrayDeque<Pair<Connection, String>>()
    private val toClients = ArrayDeque<Pair<Connection, String>>()

    private var nextClient = 1

    fun connect(session: DocumentSyncSession, username: String = "user"): Connection {
        val connection = Connection("c-${nextClient++}", username, session)
        connections += connection
        session.onConnected({ message -> if (connection.open) toServer.add(connection to message) })
        return connection
    }

    fun disconnect(connection: Connection) {
        connection.open = false
        connections -= connection
        connection.session.onDisconnected()
        toClients.removeAll { it.first == connection }
        toServer.removeAll { it.first == connection }
        connections.forEach {
            send(it, PresenceLeaveMessage(documentId = documentId, clientId = connection.clientId))
        }
    }

    /**
     * Delivers all queued messages in both directions until nothing is left
     */
    fun pump() {
        var rounds = 0
        while (toServer.isNotEmpty() || toClients.isNotEmpty()) {
            check(++rounds < 10_000) { "no convergence" }
            toServer.removeFirstOrNull()?.let { (connection, message) -> handle(connection, message) }
            toClients.removeFirstOrNull()?.let { (connection, message) ->
                if (connection.open) connection.session.onMessage(message)
            }
            closeAfterDelivery.filter { c -> toClients.none { it.first == c } }.forEach {
                closeAfterDelivery -= it
                disconnect(it)
            }
        }
    }

    /**
     * An edit on the server side (e.g. an external change of the file)
     */
    fun edit(newText: String) {
        document.applyLocalText(newText)
        connections.forEach(::sendSync)
    }

    /**
     * Moves the document to a new id, like the backend does: open connections get
     * `document-renamed` and are closed, later connections with an old id get it after hello
     */
    fun move(newId: String) {
        oldIds += documentId
        val oldId = documentId
        documentId = newId
        connections.toList().forEach {
            sendRaw(it, renamed(oldId))
            closeAfterDelivery += it
        }
    }

    private fun renamed(oldId: String) =
        """{"type":"document-renamed","documentId":"$oldId","newDocumentId":"$documentId"}"""

    /** connections the server closes (4009) once their queued messages are delivered */
    private val closeAfterDelivery = mutableSetOf<Connection>()

    /**
     * Simulates a server that lost its saved state and starts a new lineage from the file
     */
    fun restartWithLostState(fileText: String) {
        connections.toList().forEach(::disconnect)
        document = createDocument(fileText)
        epoch = "epoch-${epoch.removePrefix("epoch-").toInt() + 1}"
    }

    fun send(connection: Connection, message: Any) {
        val adapter = moshi.adapter(message.javaClass)
        toClients.add(connection to adapter.toJson(message))
    }

    fun sendRaw(connection: Connection, json: String) {
        toClients.add(connection to json)
    }

    private fun handle(connection: Connection, message: String) {
        when (moshi.adapter(MessageEnvelope::class.java).fromJson(message)!!.type) {
            MessageType.HELLO -> {
                val hello = moshi.adapter(HelloMessage::class.java).fromJson(message)!!
                receivedHellos += hello
                if (hello.documentId in oldIds) {
                    sendRaw(connection, renamed(hello.documentId))
                    closeAfterDelivery += connection
                    return
                }
                val owner = boundActors.getOrPut(hello.actorId) { connection.username }
                check(owner == connection.username) { "actor ${hello.actorId} belongs to $owner" }
                connection.syncState = SyncState()
                send(
                    connection,
                    InitialContentMessage(
                        documentId = documentId,
                        epoch = epoch,
                        documentState = Base64.getEncoder().encodeToString(document.save()),
                        content = document.text,
                        readOnly = readOnly,
                        you = UserInfo(username = connection.username, clientId = connection.clientId),
                        actors = boundActors.map { (actor, user) -> ActorInfo(actor, user) },
                    )
                )
                send(
                    connection,
                    PresenceSnapshotMessage(
                        documentId = documentId,
                        peers = connections.filter { it != connection }.map {
                            it.presence ?: PresenceMessage(documentId = documentId, clientId = it.clientId, username = it.username)
                        },
                    )
                )
            }

            MessageType.SYNC_REQUEST -> {
                val request = moshi.adapter(SyncRequestMessage::class.java).fromJson(message)!!
                val state = connection.syncState ?: return
                if (readOnly) {
                    // readers sync against a shadow; simplified: drop their changes
                    val shadow = AutomergeTextDocument.load(document.save())
                    shadow.receiveSyncMessage(state, Base64.getDecoder().decode(request.syncMessage))
                    return
                }
                document.receiveSyncMessage(state, Base64.getDecoder().decode(request.syncMessage))
                connections.forEach(::sendSync)
            }

            MessageType.PRESENCE -> {
                val presence = moshi.adapter(PresenceMessage::class.java).fromJson(message)!!.copy(
                    clientId = connection.clientId,
                    username = connection.username,
                    color = "#123456",
                )
                connection.presence = presence
                connections.filter { it != connection }.forEach { send(it, presence) }
            }
        }
    }

    private fun sendSync(connection: Connection) {
        val state = connection.syncState ?: return
        while (true) {
            val message = document.generateSyncMessage(state) ?: break
            send(
                connection,
                SyncRequestMessage(documentId = documentId, syncMessage = Base64.getEncoder().encodeToString(message))
            )
        }
    }

    private fun createDocument(text: String): AutomergeTextDocument {
        val doc = Document()
        doc.startTransaction().use { tx ->
            val content = tx.set(ObjectId.ROOT, AutomergeTextDocument.CONTENT_KEY, ObjectType.TEXT)
            tx.spliceText(content, 0, 0, text)
            tx.commit()
        }
        return AutomergeTextDocument.fromDocument(doc)
    }
}

/**
 * A [LocalDocumentStore] in memory
 */
class InMemoryDocumentStore : LocalDocumentStore {
    val documents = mutableMapOf<String, StoredDocument>()
    override fun load(documentId: String) = documents[documentId]
    override fun save(documentId: String, document: StoredDocument) {
        documents[documentId] = document
    }

    override fun ids() = documents.keys.sorted()

    val backups = mutableListOf<StoredDocument>()

    override fun keepAside(documentId: String) {
        documents.remove(documentId)?.let { backups += it }
    }

    override fun move(fromDocumentId: String, toDocumentId: String) {
        documents.remove(fromDocumentId)?.let { documents[toDocumentId] = it }
    }
}

/**
 * Edits the local copy to the given text at its current state
 *
 * @return false if the document refused the edit
 */
fun DocumentSyncSession.onLocalTextChanged(newText: String): Boolean {
    val base = text ?: return false
    val version = version ?: return false
    return applyLocalEdit(version, base, TextDiff.diff(base, newText)) != null
}
