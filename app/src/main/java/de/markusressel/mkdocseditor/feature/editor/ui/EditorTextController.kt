package de.markusressel.mkdocseditor.feature.editor.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.domain.FormatEdit
import de.markusressel.mkdocseditor.feature.editor.domain.MarkdownFormatting
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.EditorFieldValue
import de.markusressel.mkdocsrestclient.sync.automerge.EditorTextBinding
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The text of the editor: connects the text field to the open document with an
 * [EditorTextBinding], resolves the cursors of other users against the field, and applies the
 * edits of the formatting actions and pickers. All calls must come from one thread, the main
 * thread in the app.
 *
 * @param now the current time in milliseconds
 * @param editModeActive whether the user may edit right now
 * @param onChanged the text, the selection or the other users changed
 * @param onEdited an edit that does not come from the text field was applied
 */
internal class EditorTextController(
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val editModeActive: () -> Boolean,
    private val onChanged: () -> Unit = {},
    private val onEdited: () -> Unit = {},
) {
    private var binding = EditorTextBinding(NoDocument, now)

    /** the other users, resolved against the text of the document */
    var peers: List<RemotePeer> = emptyList()
        private set

    private var flushJob: Job? = null

    private var focused = false

    // structural equality (the default): an unchanged field does not recompose
    private val _field = mutableStateOf(EditorField())

    /**
     * What the text field shows. This is snapshot state and not part of a flow: the field
     * must get a value in the same frame it was set, a flow would deliver it a frame later.
     */
    val field: State<EditorField> = _field

    /** the text of the document, which the field may not show yet */
    val text: String
        get() = binding.documentText

    /** the selection in [text] */
    val selection: TextRange
        get() = TextRange(binding.documentSelectionStart, binding.documentSelectionEnd)

    /** the selected text, which the pickers use as link text */
    val selectedText: String
        get() {
            val text = text
            val selection = selection
            return text.substring(selection.min.coerceIn(0, text.length), selection.max.coerceIn(0, text.length))
        }

    /** Connects the editor to another document */
    fun bind(document: EditableTextDocument) {
        flushJob?.cancel()
        binding = EditorTextBinding(document, now)
        updateInputMethodActive()
        peers = emptyList()
        sync()
    }

    /** Disconnects the editor from its document */
    fun unbind() = bind(NoDocument)

    /** Shows a text that does not come from the document, as long as there is no local copy */
    fun showDetachedText(text: String) {
        binding.showDetachedText(text)
        sync()
    }

    /** @param splices the changes, null if the whole text changed */
    fun onDocumentChanged(splices: List<TextSplice>? = null) {
        binding.onDocumentChanged(splices)
        sync()
    }

    fun onPeersChanged(peers: List<RemotePeer>) {
        this.peers = peers
        sync()
    }

    /** The user changed the content of the text field */
    fun onFieldChanged(value: TextFieldValue) {
        binding.onFieldChanged(value.toEditorFieldValue(), editable = editModeActive())
        sync()
    }

    /** The field took the value with this id, see [EditorTextBinding.onValueShown] */
    fun onValueShown(id: Long) {
        binding.onValueShown(id)
    }

    /** The text field got or lost the focus */
    fun onFocusChanged(focused: Boolean) {
        this.focused = focused
        updateInputMethodActive()
        sync()
    }

    fun select(start: Int, end: Int) {
        val length = text.length
        binding.setSelection(start.coerceIn(0, length), end.coerceIn(0, length))
        sync()
    }

    fun applyFormat(action: FormatAction) {
        val selection = selection
        apply(MarkdownFormatting.apply(action, text, selection.start, selection.end))
    }

    /** replaces the selection with Markdown from a picker */
    fun insertAtSelection(markdown: String) {
        val selection = selection
        apply(MarkdownFormatting.replaceSelection(text, selection.start, selection.end, markdown))
    }

    /** inserts Markdown after the selection, which stays part of the text */
    fun insertAfterSelection(markdown: String) {
        val end = selection.max
        apply(MarkdownFormatting.replaceSelection(text, end, end, markdown))
    }

    /** inserts several snippets after the selection */
    fun insertSnippets(snippets: List<String>) {
        apply(MarkdownFormatting.insertSnippets(text, selection.max, snippets))
    }

    /** replaces a range of [text] */
    fun replace(start: Int, end: Int, replacement: String) {
        apply(MarkdownFormatting.replaceSelection(text, start, end, replacement))
    }

    private fun apply(edit: FormatEdit) {
        binding.applyEdit(edit.text, edit.selectionStart, edit.selectionEnd)
        sync()
        onEdited()
    }

    private fun updateInputMethodActive() {
        binding.inputMethodActive = focused && editModeActive()
    }

    /**
     * Copies the state of the binding to the field, and takes care that changes the field holds
     * back get shown. Everything but the field uses the text of the document, and its selection.
     */
    private fun sync() {
        val binding = binding
        updateInputMethodActive()
        val value = binding.value
        val fieldPeers = peers.map { peer ->
            peer.copy(
                selections = peer.selections.map {
                    it.copy(anchor = binding.toFieldIndex(it.anchor), head = binding.toFieldIndex(it.head))
                }
            )
        }
        _field.value = EditorField(value.toTextFieldValue(), binding.valueId, fieldPeers)
        onChanged()
        scheduleFlush()
    }

    private fun scheduleFlush() {
        val delayMs = binding.holdDelay()
        flushJob?.cancel()
        if (delayMs == null) return
        flushJob = scope.launch {
            delay(delayMs)
            binding.flush()
            sync()
        }
    }
}

/**
 * The state of the text field of the editor
 *
 * @param value what the field shows
 * @param id identifies [value], see [EditorTextController.onValueShown]
 * @param remotePeers the other users, resolved against the text of [value]
 */
internal data class EditorField(
    val value: TextFieldValue = TextFieldValue(),
    val id: Long = 0,
    val remotePeers: List<RemotePeer> = emptyList(),
)

/** There is no document open */
private object NoDocument : EditableTextDocument {
    override val text: String? = null
    override val version: DocumentVersion? = null
    override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) = null
    override fun splicesSince(version: DocumentVersion, text: String) = emptyList<TextSplice>()
}

private fun EditorFieldValue.toTextFieldValue() = TextFieldValue(
    text = text,
    selection = TextRange(selectionStart, selectionEnd),
    composition = if (isComposing) TextRange(compositionStart!!, compositionEnd!!) else null,
)

private fun TextFieldValue.toEditorFieldValue() = EditorFieldValue(
    text = text,
    selectionStart = selection.start,
    selectionEnd = selection.end,
    compositionStart = composition?.min,
    compositionEnd = composition?.max,
)
