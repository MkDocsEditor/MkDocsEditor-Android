package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextSplice

/**
 * A state of a document: an opaque set of Automerge heads, or a state without a document.
 * Every text the editor has ever shown is one of these states, see [EditorTextBinding].
 */
class DocumentVersion internal constructor(internal val headsHex: List<String>) {
    override fun equals(other: Any?): Boolean = other is DocumentVersion && headsHex == other.headsHex
    override fun hashCode(): Int = headsHex.hashCode()
    override fun toString(): String = headsHex.joinToString(",") { it.take(8) }
}

/**
 * The document an editor edits. Implementations are not thread safe, use one thread.
 */
interface EditableTextDocument {
    /** The current text, or null while there is no local copy */
    val text: String?

    /** The current state, or null while there is no local copy */
    val version: DocumentVersion?

    /**
     * Applies an edit that was made on the text of an earlier state. Automerge merges it with
     * everything that happened since, like with an edit of another user, so it needs no
     * position arithmetic and can not undo those changes.
     *
     * @param base the state the edit was made on
     * @param baseText the text of [base]
     * @param splices the edit, relative to [baseText]
     * @return the state of the edit: [baseText] with the edit, without the changes since [base].
     * null if the document refuses edits (no local copy, or read-only).
     * @throws IllegalArgumentException if this document does not know [base]
     */
    fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>): DocumentVersion?

    /**
     * The changes from the text of an earlier state to the current text
     *
     * @throws IllegalArgumentException if this document does not know [version]
     */
    fun splicesSince(version: DocumentVersion, text: String): List<TextSplice>
}
