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
            var current = oldText
            splices.forEach { splice ->
                val start = current.codePointCount(0, splice.index)
                val deleteCount = current.codePointCount(splice.index, splice.index + splice.deleteCount)
                tx.spliceText(contentId, start.toLong(), deleteCount.toLong(), splice.insert)
                current = splice.applyTo(current)
            }
            tx.commit()
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
        val clamped = index.coerceIn(0, current.length)
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
