package de.markusressel.mkdocsrestclient.sync.automerge

import com.squareup.moshi.JsonClass
import de.markusressel.mkdocsrestclient.moshi
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

/**
 * The local copy of a document, persisted so that offline edits survive app restarts.
 */
class StoredDocument(
    /** Automerge save() of the document */
    val documentState: ByteArray,
    /** actor id (32 hex chars) used for local changes of this copy */
    val actorId: String,
    /** epoch of the server document lineage this copy belongs to */
    val epoch: String,
    /** heads (hex) the server is known to have, see the sync protocol */
    val serverHeads: List<String>,
)

/**
 * Persists the local copies of documents.
 */
interface LocalDocumentStore {
    fun load(documentId: String): StoredDocument?
    fun save(documentId: String, document: StoredDocument)

    /**
     * Moves a stored document to a new id (after a rename on the server).
     */
    fun move(fromDocumentId: String, toDocumentId: String)
}

/**
 * Stores each document as one JSON file in [directory]. Writes are atomic
 * (temporary file plus rename), so a crash never leaves a half written copy.
 */
class FileDocumentStore(
    private val directory: File,
) : LocalDocumentStore {

    @JsonClass(generateAdapter = true)
    internal data class StoredDocumentJson(
        val documentId: String,
        val documentState: String,
        val actorId: String,
        val epoch: String,
        val serverHeads: List<String>,
    )

    private val adapter = moshi.adapter(StoredDocumentJson::class.java)

    @Synchronized
    override fun load(documentId: String): StoredDocument? {
        val file = fileFor(documentId)
        if (!file.exists()) {
            return null
        }
        val json = adapter.fromJson(file.readText()) ?: return null
        return StoredDocument(
            documentState = Base64.getDecoder().decode(json.documentState),
            actorId = json.actorId,
            epoch = json.epoch,
            serverHeads = json.serverHeads,
        )
    }

    @Synchronized
    override fun save(documentId: String, document: StoredDocument) {
        directory.mkdirs()
        val json = StoredDocumentJson(
            documentId = documentId,
            documentState = Base64.getEncoder().encodeToString(document.documentState),
            actorId = document.actorId,
            epoch = document.epoch,
            serverHeads = document.serverHeads,
        )
        val target = fileFor(documentId)
        val temp = File(directory, target.name + ".tmp")
        temp.writeText(adapter.toJson(json))
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Could not write $target")
        }
    }

    @Synchronized
    override fun move(fromDocumentId: String, toDocumentId: String) {
        val document = load(fromDocumentId) ?: return
        save(toDocumentId, document)
        fileFor(fromDocumentId).delete()
    }

    private fun fileFor(documentId: String): File {
        val name = if (documentId.matches(SAFE_NAME)) {
            documentId
        } else {
            MessageDigest.getInstance("SHA-256").digest(documentId.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }
        return File(directory, "$name.json")
    }

    companion object {
        private val SAFE_NAME = Regex("[A-Za-z0-9_-]{1,100}")
    }
}
