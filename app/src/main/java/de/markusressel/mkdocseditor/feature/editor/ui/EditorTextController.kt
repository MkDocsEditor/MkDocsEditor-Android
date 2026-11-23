package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.toAuthorRuns
import de.markusressel.mkdocsrestclient.reference.AuthorshipModel
import androidx.compose.runtime.State
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocsrestclient.sync.automerge.AgentReveal
import de.markusressel.mkdocseditor.feature.editor.domain.AgentReveals
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
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

    /** the peers as the document reports them, see [peers] */
    private var reportedPeers = emptyList<RemotePeer>()

    private val _peers = mutableStateOf(emptyList<RemotePeer>())

    /** text of agents that is still being typed out */
    private val reveals = AgentReveals()

    private val _revealing = mutableStateOf(false)

    /** whether text of agents is being typed out, then [stepReveals] has to be called every [AgentReveals.STEP_MS] */
    val revealing: State<Boolean> = _revealing

    /**
     * Whether live changes of agents may be typed out: the editor is shown and visible, the setting "Animate edits of
     * others" is on, and the system does not remove animations. Turned off, the rest shows at once.
     */
    var revealAllowed: Boolean = false
        set(value) {
            field = value
            if (!value && !reveals.isEmpty) {
                reveals.finishAll()
                showReveals()
            }
        }

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

    /**
     * Who wrote what, moved within the call of each edit like [peers]. The user's own edits and those of a known remote
     * author get their author at once, the backend's answer replaces everything with the exact attribution.
     */
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
                if (!applyingDocument) {
                    // a tap on the text that is being typed out shows it at once; a cursor elsewhere does not
                    if (selection.collapsed && reveals.onTap(selection.start)) showReveals()
                    onSelectionChanged(selection)
                }
            }
        })
    }

    /** Connects the editor to another document */
    fun bind(document: EditableTextDocument) {
        this.document = document
        reveals.finishAll()
        showReveals()
        // a fade of the old document would show on the text of the new one
        editorState.finishAppearing()
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

    private fun moveAuthors(start: Int, removed: Int, inserted: Int, author: String?) {
        _authorRuns.value = _authorRuns.value.applied(start, removed, inserted, author)
        onDocumentChanged()
    }

    /**
     * Another user changed the document. The inserted text fades in, if the editor animates that; typing of the user
     * never ends such a fade, many people can write at the same time.
     *
     * @param text the text of the document after the change
     * @param splices the change, each one refers to the text after the one before it
     * @param author who made all of [splices], null if not known
     * @param agentReveals live changes of agents among [splices], whose text is typed out if [revealAllowed]
     */
    fun onRemoteChange(text: String, splices: List<TextSplice>, author: String? = null, agentReveals: List<AgentReveal> = emptyList()) {
        var fits = true
        showInEditor {
            editorState.applyRemote(
                splices.map { EditorSplice(it.index, it.deleteCount, it.insert) },
                appear = true,
                appearTag = author.orEmpty(),
            )
            // in the same call, so the text of an agent never shows for a frame before it is hidden
            var revealsChanged = false
            splices.forEach { revealsChanged = reveals.onEdit(it.index, it.deleteCount, it.insert.length) || revealsChanged }
            agentReveals.forEach {
                // a new change of the same agent finishes its running reveal first
                val running = it.clientId in reveals.heads
                if (revealAllowed) reveals.start(it.clientId, it.instanceId, it.ranges) else reveals.finish(it.clientId)
                revealsChanged = revealsChanged || running || it.clientId in reveals.heads
            }
            if (revealsChanged || agentReveals.isNotEmpty()) showReveals()
            // an edit on a wrong base text would change the wrong characters of the document
            if (editorState.text != text) {
                Timber.e("The editor differs from the document after a remote change")
                editorState.setText(text)
                fits = false
            }
        }
        if (!fits) {
            reveals.finishAll()
            showReveals()
            return forgetAuthors()
        }
        splices.forEach { moveAuthors(it.index, it.deleteCount, it.insert.length, author) }
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
        reportedPeers = peers
        _authorColors.value = authorColors + peers.associate { it.username to it.color }
        publishPeers()
    }

    /** An agent that types out its text has its cursor at the head of what is shown, and no viewport to jump to. */
    private fun publishPeers() {
        val heads = reveals.heads
        _peers.value = if (heads.isEmpty()) reportedPeers else reportedPeers.map { peer ->
            heads[peer.clientId]?.let { peer.copy(selections = listOf(RemoteSelection(it, it)), viewport = null) } ?: peer
        }
        onPeersChanged()
    }

    /**
     * What is folded at a position in the editor, from its layout while it is shown. The reveal shows a folded part as
     * a whole.
     */
    var collapsedRangeAt: (Int) -> TextRange? = { null }

    /** Moves the reveals on, see [revealing] */
    fun stepReveals(elapsedMs: Long) {
        if (reveals.isEmpty) return
        val text = editorState.text
        reveals.step(elapsedMs, text) { collapsedRangeAt(it)?.max }.forEach { editorState.appear(it.start, it.end, it.tag) }
        showReveals()
    }

    /** hides what is still to be typed out, keeping its space */
    private fun showReveals() {
        editorState.setRangeStyles(reveals.hiddenRanges.map { AnnotatedString.Range(HIDDEN, it.first, it.last + 1) })
        _revealing.value = !reveals.isEmpty
        publishPeers()
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

    /**
     * Changes the text to [newText] as one edit of the part that differs, so the rest of the text and the cursors in it
     * stay as they are. The selection stays where it is in the text around the change.
     */
    fun changeTo(newText: String) {
        val before = text
        var start = 0
        while (start < before.length && start < newText.length && before[start] == newText[start]) start++
        // never between the two halves of a character
        if (start > 0 && before[start - 1].isHighSurrogate()) start--
        var same = 0
        while (same < before.length - start && same < newText.length - start &&
            before[before.length - 1 - same] == newText[newText.length - 1 - same]
        ) same++
        if (same > 0 && before[before.length - same].isLowSurrogate()) same--
        val end = before.length - same
        val inserted = newText.substring(start, newText.length - same)
        if (start == end && inserted.isEmpty()) return

        fun moved(position: Int) = when {
            position <= start -> position
            position >= end -> position + inserted.length - (end - start)
            else -> start + inserted.length
        }
        val selection = selection
        apply(FormatEdit(newText, moved(selection.start), moved(selection.end)))
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
            moveAuthors(splice.start, splice.removedLength, splice.inserted.length, document.localAuthor)
            // typing into text that is still hidden shows it
            reveals.onEdit(splice.start, splice.removedLength, splice.inserted.length)
            if (!reveals.isEmpty || editorState.rangeStyles.isNotEmpty()) showReveals()
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

/** the text an agent has not typed yet keeps its space */
private val HIDDEN = SpanStyle(color = Color.Transparent)

/** There is no document open */
private object NoDocument : EditableTextDocument {
    override val text: String? = null
    override val version: DocumentVersion? = null
    override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) = null
}
