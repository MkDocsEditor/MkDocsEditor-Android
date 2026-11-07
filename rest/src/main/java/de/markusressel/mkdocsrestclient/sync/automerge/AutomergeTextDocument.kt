package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextDiff
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.applySplices
import org.automerge.AmValue
import org.automerge.ChangeHash
import org.automerge.Cursor
import org.automerge.Document
import org.automerge.ObjectId
import org.automerge.Patch
import org.automerge.PatchAction
import org.automerge.PatchLog
import org.automerge.SyncState
import org.automerge.Transaction
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Inflater

/**
 * An Automerge document holding the text of one markdown file in the
 * [CONTENT_KEY] text object of its root map.
 *
 * All text positions in this API are UTF-16 indices (Kotlin [String] indices).
 * Automerge itself counts Unicode code points; this class converts between the two.
 *
 * The content object is never replaced, local changes are always applied as splices.
 * Not thread safe: use one instance from one thread at a time.
 */
class AutomergeTextDocument private constructor(
    val document: Document,
    private val contentId: ObjectId,
) {

    /**
     * The current text of the document
     */
    val text: String
        get() = document.text(contentId).orElse("")

    val heads: Array<ChangeHash>
        get() = document.heads

    /**
     * The heads as hex strings, for persisting them
     */
    val headsHex: List<String>
        get() = document.heads.map { it.toHex() }

    /** The current state of the document */
    val version: DocumentVersion
        get() = DocumentVersion(headsHex)

    /**
     * Applies an edit made on the text of an earlier state in one change. The change has that
     * state as its parent, so it is concurrent to everything that happened since, and Automerge
     * merges them the same way on all copies.
     *
     * @param splices the edit, relative to [baseText], the text of [base]
     * @return the state of the change: [baseText] with the edit
     * @throws IllegalArgumentException if this document has no state [base]
     */
    fun applySplicesAt(base: DocumentVersion, baseText: String, splices: List<TextSplice>): DocumentVersion {
        if (splices.isEmpty()) {
            return base
        }
        if (base == version) {
            applyLocalSplices(baseText, splices)
            return version
        }
        val heads = base.toHeads(baseText)
        val patchLog = PatchLog()
        try {
            val hash = document.startTransactionAt(patchLog, heads).use { tx ->
                spliceAll(tx, baseText, splices)
                tx.commit()
            }
            return DocumentVersion(listOf(hash.orElseThrow().bytes.toHex()))
        } finally {
            patchLog.free()
        }
    }

    /**
     * The heads of a state that should have this text. Heads of another copy (a new epoch) are
     * not always an error for Automerge, they can just give some other text.
     */
    private fun DocumentVersion.toHeads(expectedText: String): Array<ChangeHash> {
        val heads = headsHex.map { RestoredChangeHash(it.hexToBytes()) as ChangeHash }.toTypedArray()
        val text = try {
            document.text(contentId, heads).orElse(null)
        } catch (e: RuntimeException) {
            null
        }
        require(text == expectedText) { "Unknown document state $this" }
        return heads
    }

    /**
     * The text of the document at the given heads (hex)
     */
    fun textAt(headsHex: List<String>): String {
        val heads = headsHex.map { RestoredChangeHash(it.hexToBytes()) as ChangeHash }.toTypedArray()
        return document.text(contentId, heads).orElse("")
    }

    val actorId: ByteArray
        get() = document.actorId

    /**
     * Applies a new editor text to the document as minimal splices in one change.
     *
     * @return the splices that were applied (relative to the previous text)
     */
    fun applyLocalText(newText: String): List<TextSplice> {
        val oldText = text
        val splices = TextDiff.diff(oldText, newText)
        applyLocalSplices(oldText, splices)
        return splices
    }

    /**
     * Applies the given splices (relative to the current text) in one change.
     */
    fun applyLocalSplices(splices: List<TextSplice>) {
        applyLocalSplices(text, splices)
    }

    private fun applyLocalSplices(oldText: String, splices: List<TextSplice>) {
        if (splices.isEmpty()) {
            return
        }
        document.startTransaction().use { tx ->
            spliceAll(tx, oldText, splices)
            tx.commit()
        }
    }

    private fun spliceAll(tx: Transaction, oldText: String, splices: List<TextSplice>) {
        var current = oldText
        splices.forEach { splice ->
            val start = current.codePointCount(0, splice.index)
            val deleteCount = current.codePointCount(splice.index, splice.index + splice.deleteCount)
            tx.spliceText(contentId, start.toLong(), deleteCount.toLong(), splice.insert)
            current = splice.applyTo(current)
        }
    }

    /**
     * Receives a sync message from a peer.
     *
     * @return the resulting changes of the text, as splices relative to the text before
     */
    fun receiveSyncMessage(syncState: SyncState, message: ByteArray): List<TextSplice> {
        return trackTextChanges { patchLog ->
            document.receiveSyncMessage(syncState, patchLog, message)
        }
    }

    /**
     * Generates the next sync message for a peer, or null if there is nothing to send.
     */
    fun generateSyncMessage(syncState: SyncState): ByteArray? {
        return document.generateSyncMessage(syncState).orElse(null)
    }

    /**
     * Merges all changes of another copy of this document.
     *
     * @return the resulting changes of the text, as splices relative to the text before
     */
    fun merge(other: AutomergeTextDocument): List<TextSplice> {
        return trackTextChanges { patchLog ->
            document.merge(other.document, patchLog)
        }
    }

    /**
     * Creates an Automerge cursor for a UTF-16 position in the current text.
     * The cursor keeps pointing at the same character across concurrent edits.
     */
    fun makeCursor(index: Int): String {
        val current = text
        var clamped = index.coerceIn(0, current.length)
        // a position between the two halves of a surrogate pair means that character: counted from there, its first
        // half would be a code point of its own, and one behind the last character is out of bounds for Automerge
        if (clamped in 1 until current.length && current[clamped].isLowSurrogate() && current[clamped - 1].isHighSurrogate()) {
            clamped--
        }
        if (clamped == current.length) {
            // automerge-java cannot create a cursor behind the last character
            return if (clamped == 0) CURSOR_START else CURSOR_END
        }
        val codePointIndex = current.codePointCount(0, clamped)
        return document.makeCursor(contentId, codePointIndex.toLong()).toString()
    }

    /**
     * Resolves an Automerge cursor to a UTF-16 position in the current text.
     *
     * @return the position, or null if the cursor cannot be resolved (yet)
     */
    fun lookupCursor(cursor: String): Int? {
        when (cursor) {
            CURSOR_START -> return 0
            CURSOR_END -> return text.length
        }
        return try {
            val codePointIndex = document.lookupCursorIndex(contentId, Cursor.fromString(cursor)).toInt()
            val current = text
            current.offsetByCodePoints(0, codePointIndex.coerceIn(0, current.codePointCount(0, current.length)))
        } catch (e: Exception) {
            null
        }
    }

    fun save(): ByteArray = document.save()

    /**
     * automerge-java tells neither the patches nor the changes apart by actor, so this reads the actor from the header
     * of each encoded change.
     *
     * @return the actor ids (hex) of the changes that came after [heads], null if they could not be read
     */
    fun actorsSince(heads: Array<ChangeHash>): Set<String>? = try {
        changeActors(document.encodeChangesSince(heads))
    } catch (e: RuntimeException) {
        null
    } catch (e: DataFormatException) {
        null
    }

    /**
     * Runs an operation that changes the document and converts its patches to splices.
     */
    private fun trackTextChanges(operation: (PatchLog) -> Unit): List<TextSplice> {
        val before = text
        val patchLog = PatchLog()
        try {
            operation(patchLog)
            val splices = patchesToSplices(before, document.makePatches(patchLog))
            val after = text
            return if (before.applySplices(splices) == after) {
                splices
            } else {
                // should not happen, but never let the editor diverge from the document
                TextDiff.diff(before, after)
            }
        } finally {
            patchLog.free()
        }
    }

    private fun patchesToSplices(before: String, patches: List<Patch>): List<TextSplice> {
        var current = before
        val result = mutableListOf<TextSplice>()
        patches.filter { it.obj == contentId }.forEach { patch ->
            val splice = when (val action = patch.action) {
                is PatchAction.SpliceText -> {
                    val index = current.offsetByCodePoints(0, action.index.toInt())
                    TextSplice(index = index, deleteCount = 0, insert = action.text)
                }

                is PatchAction.DeleteList -> {
                    val start = current.offsetByCodePoints(0, action.index.toInt())
                    val end = current.offsetByCodePoints(start, action.length.toInt())
                    TextSplice(index = start, deleteCount = end - start, insert = "")
                }

                else -> null
            }
            if (splice != null) {
                result.add(splice)
                current = splice.applyTo(current)
            }
        }
        return result
    }

    /**
     * automerge-java's ChangeHash has no public constructor
     */
    private class RestoredChangeHash(bytes: ByteArray) : ChangeHash(bytes)

    companion object {
        const val CONTENT_KEY = "content"

        /**
         * Cursor strings for the start and the end of the text. Automerge cursors point
         * at characters, so there is no cursor behind the last character.
         */
        const val CURSOR_START = "start"
        const val CURSOR_END = "end"

        private fun ChangeHash.toHex(): String = bytes.toHex()

        /**
         * Loads a document from the output of [save].
         *
         * @param actorId the actor id to use for local changes, or null for a random one.
         * Never use the same actor id for two copies of a document that can change
         * concurrently.
         */
        fun load(bytes: ByteArray, actorId: ByteArray? = null): AutomergeTextDocument {
            val loaded = Document.load(bytes)
            val document = if (actorId != null) {
                loaded.fork(actorId).also { loaded.free() }
            } else {
                loaded
            }
            return fromDocument(document)
        }

        /**
         * Wraps an existing document.
         *
         * @throws IllegalArgumentException if the document has no [CONTENT_KEY] text
         */
        fun fromDocument(document: Document): AutomergeTextDocument {
            val content = document.get(ObjectId.ROOT, CONTENT_KEY).orElse(null)
            require(content is AmValue.Text) { "Document has no \"$CONTENT_KEY\" text object" }
            return AutomergeTextDocument(document, content.id)
        }
    }
}

/**
 * Lower case hex representation of these bytes
 */
fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * Parses a hex string (as created by [toHex])
 */
fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "invalid hex string: $this" }
    return ByteArray(length / 2) { i -> substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/** The first bytes of each chunk of the Automerge binary format */
private val CHUNK_MAGIC = byteArrayOf(0x85.toByte(), 0x6f, 0x4a, 0x83.toByte())
private const val CHUNK_CHANGE = 1
private const val CHUNK_COMPRESSED_CHANGE = 2
private const val HASH_LENGTH = 32

/**
 * Reads the actor of each change from concatenated change chunks, see the Automerge binary format: magic bytes,
 * checksum, chunk type, length, and then the dependencies and the actor of the change.
 *
 * @throws IllegalArgumentException if the bytes are not such chunks
 */
internal fun changeActors(bytes: ByteArray): Set<String> {
    val actors = mutableSetOf<String>()
    val reader = ByteReader(bytes)
    while (!reader.atEnd) {
        require(CHUNK_MAGIC.all { it == reader.byte() }) { "not an Automerge chunk" }
        reader.skip(4) // checksum
        val type = reader.byte().toInt()
        val contents = reader.bytes(reader.uLeb().toInt())
        val change = when (type) {
            CHUNK_CHANGE -> contents
            CHUNK_COMPRESSED_CHANGE -> inflate(contents)
            else -> throw IllegalArgumentException("chunk type $type is no change")
        }
        val header = ByteReader(change)
        header.skip(header.uLeb().toInt() * HASH_LENGTH) // dependencies
        actors.add(header.bytes(header.uLeb().toInt()).toHex())
    }
    return actors
}

private fun inflate(bytes: ByteArray): ByteArray {
    val inflater = Inflater(true)
    try {
        inflater.setInput(bytes)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            require(count > 0 || inflater.finished()) { "truncated compressed change" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    } finally {
        inflater.end()
    }
}

private class ByteReader(private val bytes: ByteArray) {
    private var position = 0

    val atEnd: Boolean
        get() = position >= bytes.size

    fun byte(): Byte {
        require(position < bytes.size) { "unexpected end" }
        return bytes[position++]
    }

    fun skip(count: Int) {
        require(count >= 0 && position + count <= bytes.size) { "unexpected end" }
        position += count
    }

    fun bytes(count: Int): ByteArray {
        val start = position
        skip(count)
        return bytes.copyOfRange(start, position)
    }

    fun uLeb(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = byte().toInt()
            result = result or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            require(shift < 64) { "LEB128 too long" }
        }
    }
}
