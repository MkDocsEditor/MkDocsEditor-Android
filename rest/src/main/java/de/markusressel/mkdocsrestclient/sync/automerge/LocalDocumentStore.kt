package de.markusressel.mkdocsrestclient.sync.automerge

import com.squareup.moshi.JsonClass
import de.markusressel.mkdocsrestclient.moshi
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import timber.log.Timber

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
     * The ids of all stored documents
     */
    fun ids(): List<String>

    /**
     * Moves a stored document to a new id (after the document moved or was renamed on the
     * server). A copy is never lost:
     *
     * - without a copy at the new id, the copy moves;
     * - a copy of the same lineage (epoch) at the new id, for example from an interrupted
     *   earlier move, is merged with the moved copy;
     * - a copy of another lineage at the new id is kept aside as a backup, and the moved copy
     *   takes its place (the server document at the new id is the moved one).
     */
    fun move(fromDocumentId: String, toDocumentId: String)

    /**
     * Moves the copy of a document out of the way into a backup, so that it is not lost when
     * another copy takes its id
     */
    fun keepAside(documentId: String)
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
    override fun ids(): List<String> {
        val files = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            try {
                adapter.fromJson(file.readText())?.documentId
            } catch (e: Exception) {
                Timber.e(e, "Unreadable local copy $file")
                null
            }
        }.sorted()
    }

    @Synchronized
    override fun move(fromDocumentId: String, toDocumentId: String) {
        if (fromDocumentId == toDocumentId) return
        val moved = load(fromDocumentId) ?: return
        val existing = load(toDocumentId)
        val result = when {
            existing == null -> moved
            existing.epoch == moved.epoch -> merge(existing, moved) ?: run {
                // keep both: the moved copy as a backup, the existing copy stays
                keepAside(fromDocumentId)
                return
            }

            else -> {
                keepAside(toDocumentId)
                moved
            }
        }
        // the new copy is complete on disk before the old one goes
        save(toDocumentId, result)
        fileFor(fromDocumentId).delete()
    }

    /**
     * Copies of documents that were replaced by a move, kept so that no edit is ever lost
     */
    @Synchronized
    fun backups(): List<File> = File(directory, BACKUP_DIRECTORY).listFiles()?.sortedBy { it.name } ?: emptyList()

    /**
     * Merges two copies of the same lineage. Uses the actor id and server heads of [target],
     * which belongs to the new id.
     */
    private fun merge(target: StoredDocument, other: StoredDocument): StoredDocument? = try {
        val document = AutomergeTextDocument.load(target.documentState, target.actorId.hexToBytes())
        document.merge(AutomergeTextDocument.load(other.documentState))
        StoredDocument(
            documentState = document.save(),
            actorId = target.actorId,
            epoch = target.epoch,
            serverHeads = target.serverHeads.ifEmpty { other.serverHeads },
        )
    } catch (e: Exception) {
        Timber.e(e, "Could not merge two local copies")
        null
    }

    @Synchronized
    override fun keepAside(documentId: String) {
        val file = fileFor(documentId)
        if (!file.exists()) return
        val backups = File(directory, BACKUP_DIRECTORY).apply { mkdirs() }
        val backup = File(backups, "${file.nameWithoutExtension}-${System.currentTimeMillis()}.json")
        if (!file.renameTo(backup)) {
            throw IOException("Could not keep $file aside")
        }
        Timber.w("Kept the local copy of $documentId aside as $backup")
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
        private const val BACKUP_DIRECTORY = "replaced"
    }
}
