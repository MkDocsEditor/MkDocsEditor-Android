package de.markusressel.mkdocsrestclient.sync.automerge

import de.markusressel.mkdocsrestclient.sync.text.TextDiff
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import de.markusressel.mkdocsrestclient.sync.text.applySplices
import de.markusressel.mkdocsrestclient.sync.text.rebaseSplices
import de.markusressel.mkdocsrestclient.sync.text.transformIndex
import de.markusressel.mkdocsrestclient.sync.text.untransformIndex
import timber.log.Timber

/**
 * The state of a text field: text, selection and the region an input method is composing.
 * Indices are UTF-16 code units.
 */
data class EditorFieldValue(
    val text: String,
    val selectionStart: Int = 0,
    val selectionEnd: Int = selectionStart,
    val compositionStart: Int? = null,
    val compositionEnd: Int? = null,
) {
    val isComposing: Boolean
        get() = compositionStart != null && compositionEnd != null && compositionStart != compositionEnd
}

/**
 * Connects a text field to an [EditableTextDocument], so that local input and remote changes
 * can never overwrite each other.
 *
 * A Compose text field keeps its own buffer. It applies input from the input method to that
 * buffer right away, and takes a new value from outside only in the next frame. So when a key
 * press arrives, the field may not have seen the latest remote change, and the text it reports
 * is not the document text plus the key press. Comparing the two would delete the remote change.
 *
 * This class therefore remembers which state of the document the field shows. An edit of the
 * field is the difference to that state, and goes into the document at that state, so Automerge
 * merges it with the changes the field has not seen. The document stays the only source of
 * truth: the field gets the document text whenever that is possible.
 *
 * Giving a text field a new text makes it commit the composing text and restart the input
 * method (`EditProcessor.reset` in Compose), which ends autocorrect and swipe input, and input
 * arriving at that moment gets lost. So the field keeps its text while the user composes a word
 * or types, and gets the changes in the next pause, see [holdDelay].
 *
 * All calls must come from one thread, the main thread in the app.
 *
 * @param now the current time in milliseconds
 */
class EditorTextBinding(
    private val document: EditableTextDocument,
    private val now: () -> Long,
) {

    /** the text of the field's buffer and the document state it belongs to */
    private var fieldText: String = document.text.orEmpty()

    /** null when the buffer does not belong to any state of the document */
    private var fieldVersion: DocumentVersion? = document.version

    /** what the user sees of the selection and composition, relative to [fieldText] */
    private var fieldState = EditorFieldValue(fieldText)

    /** the changes from [fieldText] to [documentText], null if they must be computed again */
    private var pending: List<TextSplice>? = emptyList()

    /** shown when there is no local copy yet */
    private var detachedText: String = ""

    private var lastInputAt: Long = Long.MIN_VALUE / 2
    private var holding = false

    /** when the field last got a text it did not have, which restarts the input method */
    private var lastRestartAt: Long = Long.MIN_VALUE / 2

    /** a selection set from outside the field (in document text) that it has not shown yet */
    private var requestedSelection: Pair<Int, Int>? = null

    /** true while an edit of the field is on its way into the document */
    private var applyingEdit = false

    /** the state belonging to [value], when it has another text than the field */
    private var publishedVersion: DocumentVersion? = fieldVersion

    /**
     * true while an input method can talk to the field (focused and editable). Only then a new
     * text restarts the input method, so only then new texts are limited to one in
     * [MIN_RESTART_INTERVAL_MS]. Each restart is a short moment in which a key press gets lost.
     */
    var inputMethodActive: Boolean = false
        set(active) {
            if (field == active) return
            field = active
            publish()
        }

    /**
     * What the text field must show. Changes with every call that changes this binding.
     */
    var value: EditorFieldValue = fieldState
        private set

    /**
     * Identifies [value], for [onValueShown]
     */
    var valueId: Long = 0
        private set

    /** The text of the document */
    val documentText: String
        get() = document.text ?: detachedText

    /** The selection start in the text of the document */
    val documentSelectionStart: Int
        get() = toDocumentIndex(value, value.selectionStart)

    /** The selection end in the text of the document */
    val documentSelectionEnd: Int
        get() = toDocumentIndex(value, value.selectionEnd)

    /**
     * A position in the text of the document as a position in [value], e.g. to draw the cursor
     * of another user.
     */
    fun toFieldIndex(index: Int): Int {
        if (applyingEdit) return index.coerceIn(0, value.text.length)
        if (value.text == documentText) return index
        return untransformIndex(index, pendingSplices()).coerceIn(0, value.text.length)
    }

    private fun toDocumentIndex(shown: EditorFieldValue, index: Int): Int {
        if (applyingEdit || shown.text == documentText) return index
        return transformIndex(index, pendingSplices()).coerceIn(0, documentText.length)
    }

    /**
     * The field reported a new state, from input or from moving the cursor.
     *
     * @param editable false to refuse changes of the text (read-only). The field goes back to
     * the document text.
     */
    fun onFieldChanged(new: EditorFieldValue, editable: Boolean = true) {
        if (new.text != fieldText) {
            lastInputAt = now()
            val base = fieldText
            fieldText = new.text
            if (editable) {
                applyingEdit = true
                try {
                    applyFieldEdit(base, new)
                } finally {
                    applyingEdit = false
                }
            } else {
                pending = null
                fieldVersion = null
            }
        }
        fieldState = new
        requestedSelection = null
        publish()
    }

    private fun applyFieldEdit(base: String, new: EditorFieldValue) {
        val splices = TextDiff.diffAtCaret(base, new.text, new.selectionEnd)
        val version = fieldVersion
        val applied = try {
            if (version == null) null else document.applyLocalEdit(version, base, splices)
        } catch (e: RuntimeException) {
            Timber.w(e, "The field shows an unknown document state")
            null
        }
        if (applied != null) {
            fieldVersion = applied
            pending = if (applied == document.version) emptyList() else null
            return
        }
        // the document has no such state (a new epoch or first load), merge the texts
        val current = document.text
        val currentVersion = document.version
        if (current != null && currentVersion != null) {
            document.applyLocalEdit(currentVersion, current, rebaseSplices(base, new.text, current))
        }
        fieldVersion = null
        pending = null
    }

    /**
     * The field was composed with the value of [id], so its buffer holds that value now.
     */
    fun onValueShown(id: Long) {
        if (id != valueId) return
        if (fieldText == value.text && fieldVersion == publishedVersion) return
        if (inputMethodActive && fieldText != value.text) lastRestartAt = now()
        fieldText = value.text
        fieldVersion = publishedVersion
        fieldState = value
        requestedSelection = null
        pending = if (fieldVersion == document.version) emptyList() else null
    }

    /**
     * The text of the document changed through something else than this binding.
     *
     * @param splices the change, or null when it is not known
     */
    fun onDocumentChanged(splices: List<TextSplice>? = null) {
        requestedSelection = requestedSelection?.let { (start, end) ->
            if (splices != null) transformIndex(start, splices) to transformIndex(end, splices) else start to end
        }
        val known = pending
        pending = if (splices != null && known != null && known.size < MAX_PENDING) known + splices else null
        publish()
    }

    /**
     * Applies an edit of the whole text that does not come from the field (a formatting action,
     * a picker), computed on [documentText]. The field gets the new text and selection in the
     * next frame.
     *
     * @return false if the document refused the edit
     */
    fun applyEdit(newText: String, selectionStart: Int, selectionEnd: Int): Boolean {
        val current = document.text ?: return false
        val version = document.version ?: return false
        val splices = TextDiff.diff(current, newText)
        if (splices.isNotEmpty() && document.applyLocalEdit(version, current, splices) == null) return false
        pending = pending?.plus(splices)
        requestedSelection = selectionStart to selectionEnd
        publish()
        return true
    }

    /**
     * Sets the selection, in the text of the document. The field gets all changes with it.
     */
    fun setSelection(selectionStart: Int, selectionEnd: Int) {
        requestedSelection = selectionStart to selectionEnd
        publish()
    }

    /**
     * Shows a text while there is no local copy (the old offline cache). It stays until the
     * document has a text.
     */
    fun showDetachedText(text: String) {
        if (document.text != null) return
        detachedText = text
        pending = null
        requestedSelection = fieldState.selectionStart to fieldState.selectionEnd
        publish()
    }

    /**
     * How long the field keeps its text while the document differs from it, in milliseconds.
     * Call [flush] after this time. null when nothing waits.
     */
    fun holdDelay(): Long? {
        if (!holding) return null
        return (holdUntil() - now()).coerceAtLeast(0)
    }

    /**
     * Shows the changes that waited, if their time has come. See [holdDelay].
     */
    fun flush() {
        publish()
    }

    /**
     * Decides what the field shows: the document text, or its own text while the user types.
     */
    private fun publish() {
        requestedSelection?.let { (start, end) ->
            showDocumentText(start, end)
            return
        }
        if (fieldText == documentText && pending.isNullOrEmpty()) {
            holding = false
            setValue(fieldState, document.version)
            return
        }
        if (shouldHold()) {
            holding = true
            setValue(fieldState, fieldVersion)
            return
        }
        val splices = pendingSplices()
        showDocumentText(
            transformIndex(fieldState.selectionStart, splices),
            transformIndex(fieldState.selectionEnd, splices),
        )
    }

    private fun shouldHold(): Boolean = now() < holdUntil()

    private fun holdUntil(): Long {
        val idle = if (fieldState.isComposing) COMPOSING_IDLE_MS else TYPING_IDLE_MS
        val inputPause = lastInputAt + idle
        return if (inputMethodActive) maxOf(inputPause, lastRestartAt + MIN_RESTART_INTERVAL_MS) else inputPause
    }

    private fun showDocumentText(selectionStart: Int, selectionEnd: Int) {
        val text = documentText
        holding = false
        setValue(
            EditorFieldValue(
                text = text,
                selectionStart = selectionStart.coerceIn(0, text.length),
                selectionEnd = selectionEnd.coerceIn(0, text.length),
            ),
            document.version,
        )
    }

    private fun setValue(new: EditorFieldValue, version: DocumentVersion?) {
        publishedVersion = version
        if (new != value) {
            value = new
            valueId++
        }
    }

    /**
     * The changes from [fieldText] to [documentText]. Comes from Automerge where possible, it
     * knows exactly what changed. A text diff only guesses, but it always gives the right text.
     */
    private fun pendingSplices(): List<TextSplice> {
        pending?.let { return it }
        val text = documentText
        val version = fieldVersion
        val splices = try {
            version?.let { document.splicesSince(it, fieldText) }
                ?.takeIf { fieldText.applySplices(it) == text }
        } catch (e: RuntimeException) {
            Timber.w(e, "The field shows an unknown document state")
            null
        } ?: TextDiff.diff(fieldText, text)
        pending = splices
        return splices
    }

    companion object {
        /**
         * While composing, changes wait for the end of the word. After this time without input
         * they are shown anyway: the composing text stays in the document, the composition ends.
         */
        const val COMPOSING_IDLE_MS = 2_000L

        /**
         * Outside of a composition, changes wait for a pause of typing. Key presses that arrive
         * while the input method restarts get lost, on two emulators about one per restart.
         * So there is no upper limit: someone who types without a pause sees the changes of the
         * others a bit later, but nothing is lost.
         */
        const val TYPING_IDLE_MS = 1_000L

        /** see [inputMethodActive] */
        const val MIN_RESTART_INTERVAL_MS = 1_500L

        private const val MAX_PENDING = 1_000
    }
}
