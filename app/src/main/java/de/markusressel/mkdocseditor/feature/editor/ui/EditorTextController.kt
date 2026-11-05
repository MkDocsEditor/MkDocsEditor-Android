package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.toAuthorRuns
import de.markusressel.mkdocsrestclient.reference.AuthorshipModel
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.KodeEditorState
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.domain.FormatEdit
import de.markusressel.mkdocseditor.feature.editor.domain.MarkdownFormatting
import de.markusressel.mkdocsrestclient.sync.automerge.DocumentVersion
import de.markusressel.mkdocsrestclient.sync.automerge.EditableTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePosition
import de.markusressel.mkdocsrestclient.sync.text.TextSplice
import timber.log.Timber
import de.markusressel.kodeeditor.library.compose.TextSplice as EditorSplice

/**
 * The text of the editor: connects the [KodeEditorState] to the open document, and applies the
 * edits of the formatting actions and pickers.
 *
 * The state takes every change synchronously, so the editor and the document show the same text
 * at the end of each call: an edit of the user goes into the document within the call that made
 * it, and a change of the document goes into the editor within the call that reports it. All
 * calls must come from the main thread.
 *
 * @param editModeActive whether the user may edit right now
 * @param onPeersChanged the other users changed
 * @param onSelectionChanged the user moved the selection
 * @param onEdited an edit that does not come from the user typing in the editor was applied
 * @param onLocalEdit the user changed the text, by typing or with an action
 * @param onDocumentChanged the text changed in any way
 */
internal class EditorTextController(
    private val editModeActive: () -> Boolean,
    private val onPeersChanged: () -> Unit = {},
    private val onSelectionChanged: (TextRange) -> Unit = {},
    private val onEdited: () -> Unit = {},
    private val onLocalEdit: () -> Unit = {},
    private val onDocumentChanged: () -> Unit = {},
) {
    /** what the editor shows */
    val editorState = KodeEditorState()

    private var document: EditableTextDocument = NoDocument

    /**
     * The text of the current version of the document, which is the text of the editor before
     * the edit it reports. Reading it from the document takes too long for each key press.
     */
    private var documentText = ""

    /** an edit of an action, which does not depend on [editModeActive] */
    private var applyingAction = false

    /** the change at hand comes from the document, so the user did not move the selection */
    private var applyingDocument = false

    private var reportedSelection = editorState.selection

    private val _peers = mutableStateOf(emptyList<RemotePeer>())

    /**
     * The other users, resolved against the text of the document. This is snapshot state and not
     * part of a flow: a flow would deliver it a frame after the text it belongs to.
     */
    val peers: State<List<RemotePeer>> = _peers

    private val _lastPositions = mutableStateOf(emptyMap<String, RemotePosition>())

    /** where accounts last were, by account. Like [peers] this is snapshot state. */
    val lastPositions: State<Map<String, RemotePosition>> = _lastPositions

    private val _ghosts = mutableStateOf(emptyList<RemoteGhost>())

    /** the cursors of users who have left, for a while. Like [peers] this is snapshot state. */
    val ghosts: State<List<RemoteGhost>> = _ghosts

    private val _presenceKnown = mutableStateOf(false)

    /** whether [peers] and [ghosts] say who is here. They are empty while disconnected, which says nothing about that. */
    val presenceKnown: State<Boolean> = _presenceKnown

    private val _authorRuns = mutableStateOf(AuthorRuns())

    /** who wrote what, moved within the call of each edit like [peers]. Empty until the backend told us. */
    val authorRuns: State<AuthorRuns> = _authorRuns

    private val _authorColors = mutableStateOf(emptyMap<String, String?>())

    /**
     * The colors of the users seen so far, by username. They stay when a user leaves. This is snapshot state, because a
     * color can arrive after the runs that use it, and those have to be drawn again then.
     */
    val authorColors: Map<String, String?>
        get() = _authorColors.value

    /** the text of the document */
    val text: String
        get() = editorState.text

    /** the selection in [text] */
    val selection: TextRange
        get() = editorState.selection

    /** the selected text, which the pickers use as link text */
    val selectedText: String
        get() {
            val selection = selection
            return text.substring(selection.min, selection.max)
        }

    init {
        editorState.addListener(object : KodeEditorListener {
            override fun onLocalEdit(splice: EditorSplice) = applyToDocument(splice)

            override fun onStateChanged() {
                val selection = editorState.selection
                if (selection == reportedSelection) return
                reportedSelection = selection
                if (!applyingDocument) onSelectionChanged(selection)
            }
        })
    }

    /** Connects the editor to another document */
    fun bind(document: EditableTextDocument) {
        this.document = document
        showInEditor { editorState.setText(document.text.orEmpty(), TextRange.Zero) }
        onPeersChanged(emptyList())
        onGhostsChanged(emptyList())
        onPresenceKnownChanged(false)
        forgetAuthors()
    }

    /** Disconnects the editor from its document */
    fun unbind() = bind(NoDocument)

    /** Shows a text that does not come from the document, as long as there is no local copy */
    fun showDetachedText(text: String) {
        if (document.text != null) return
        showInEditor { editorState.setText(text) }
        forgetAuthors()
    }

    /** The whole text of the document changed */
    fun onTextReplaced() {
        showInEditor { editorState.setText(document.text ?: documentText) }
        forgetAuthors()
    }

    /** Runs of another state of the text can't be moved, so they only come back from the backend */
    private fun forgetAuthors() {
        _authorRuns.value = AuthorRuns()
        onDocumentChanged()
    }

    /**
     * @param model the answer of the backend
     * @return false if it describes another state than the current one, or does not fit the text
     */
    fun showAuthors(model: AuthorshipModel): Boolean {
        if (document.version?.hasHeads(model.heads) != true) return false
        _authorRuns.value = model.toAuthorRuns(documentText) ?: return false
        return true
    }

    private fun moveAuthors(start: Int, removed: Int, inserted: Int) {
        _authorRuns.value = _authorRuns.value.applied(start, removed, inserted)
        onDocumentChanged()
    }

    /**
     * Another user changed the document
     *
     * @param text the text of the document after the change
     * @param splices the change, each one refers to the text after the one before it
     */
    fun onRemoteChange(text: String, splices: List<TextSplice>) {
        var fits = true
        showInEditor {
            editorState.applyRemote(splices.map { EditorSplice(it.index, it.deleteCount, it.insert) })
            // an edit on a wrong base text would change the wrong characters of the document
            if (editorState.text != text) {
                Timber.e("The editor differs from the document after a remote change")
                editorState.setText(text)
                fits = false
            }
        }
        if (!fits) return forgetAuthors()
        splices.forEach { moveAuthors(it.index, it.deleteCount, it.insert.length) }
    }

    fun onAuthorColorsChanged(colors: Map<String, String?>) {
        _authorColors.value = authorColors + colors
    }

    fun onLastPositionsChanged(positions: Map<String, RemotePosition>) {
        _lastPositions.value = positions
    }

    fun onGhostsChanged(ghosts: List<RemoteGhost>) {
        _ghosts.value = ghosts
        onPeersChanged()
    }

    fun removeGhost(instanceId: String) = onGhostsChanged(ghosts.value.filterNot { it.instanceId == instanceId })

    fun onPresenceKnownChanged(known: Boolean) {
        _presenceKnown.value = known
    }

    fun onPeersChanged(peers: List<RemotePeer>) {
        _peers.value = peers
        _authorColors.value = authorColors + peers.associate { it.username to it.color }
        onPeersChanged()
    }

    fun select(start: Int, end: Int) {
        val length = text.length
        editorState.setSelection(TextRange(start.coerceIn(0, length), end.coerceIn(0, length)))
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
        applyingAction = true
        try {
            editorState.edit(edit.text, TextRange(edit.selectionStart, edit.selectionEnd))
        } finally {
            applyingAction = false
        }
        onEdited()
    }

    /** An edit the document refuses goes away again, so the user does not edit a text nobody keeps. */
    private fun applyToDocument(splice: EditorSplice) {
        val version = document.version.takeIf { applyingAction || editModeActive() }
        val applied = version != null && applyToDocument(version, splice)
        if (applied) {
            documentText = editorState.text
            moveAuthors(splice.start, splice.removedLength, splice.inserted.length)
            onLocalEdit()
        } else {
            onTextReplaced()
        }
    }

    private fun applyToDocument(version: DocumentVersion, splice: EditorSplice): Boolean {
        val splices = listOf(TextSplice(splice.start, splice.removedLength, splice.inserted))
        return try {
            document.applyLocalEdit(version, documentText, splices) != null
        } catch (e: RuntimeException) {
            Timber.e(e, "Could not apply an edit to the document")
            false
        }
    }

    private inline fun showInEditor(change: () -> Unit) {
        applyingDocument = true
        try {
            change()
        } finally {
            applyingDocument = false
        }
        reportedSelection = editorState.selection
        documentText = editorState.text
    }
}

/** There is no document open */
private object NoDocument : EditableTextDocument {
    override val text: String? = null
    override val version: DocumentVersion? = null
    override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) = null
}
