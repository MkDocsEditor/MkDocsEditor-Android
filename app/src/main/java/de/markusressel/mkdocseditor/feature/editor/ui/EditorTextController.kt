package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.feature.editor.domain.AuthorRuns
import de.markusressel.mkdocseditor.feature.editor.domain.toAuthorRuns
import de.markusressel.mkdocsrestclient.reference.AuthorshipModel
import androidx.compose.runtime.State
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteSelection
import de.markusressel.mkdocsrestclient.sync.automerge.AgentReveal
import de.markusressel.mkdocseditor.feature.editor.domain.splitAgentText
import androidx.compose.runtime.mutableStateOf
import de.markusressel.kodeeditor.library.compose.layout.KodeEditorLayout
import de.markusressel.kodeeditor.library.compose.reveal.KodeEditorReveal
import androidx.compose.ui.text.TextRange
import de.markusressel.kodeeditor.library.compose.state.KodeEditorListener
import de.markusressel.kodeeditor.library.compose.state.KodeEditorState
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
import de.markusressel.kodeeditor.library.compose.state.TextSplice as EditorSplice

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

    private val _agentReveals = mutableStateOf(emptyMap<String, KodeEditorReveal>())

    /**
     * The text of each agent that is typed out, by client id. The editor runs them with [KodeEditorReveal.keepRevealing]
     * while it is shown, and calls [onRevealsMoved] when they move.
     */
    val agentReveals: State<Map<String, KodeEditorReveal>> = _agentReveals

    /**
     * Whether live changes of agents may be typed out: the editor is shown and visible, the setting "Animate edits of
     * others" is on, and the system does not remove animations. Turned off, the rest shows at once.
     */
    var revealAllowed: Boolean = false
        set(value) {
            field = value
            if (!value) stopReveals()
        }

    /** the layout of the editor while it is shown, so the reveals show a folded part as a whole */
    var layout: KodeEditorLayout? = null
        set(value) {
            field = value
            agentReveals.value.values.forEach { it.layout = value }
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
                // a cursor in the text that is typed out shows it at once, the reveal does that itself
                if (!applyingDocument) onSelectionChanged(selection)
            }
        })
    }

    /** Connects the editor to another document */
    fun bind(document: EditableTextDocument) {
        this.document = document
        stopReveals()
        _agentReveals.value = emptyMap()
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
        stopReveals()
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
        // a long change shows at once, after what the agent still types out
        val typedOut = agentReveals.filter { reveal ->
            val length = reveal.ranges.sumOf { it.last + 1 - it.first }
            (revealAllowed && length in 1..MAX_REVEAL_LENGTH).also { if (!it) _agentReveals.value[reveal.clientId]?.stop() }
        }
        val split = splitAgentText(text, splices, typedOut.groupBy({ it.clientId }, { it.ranges }).mapValues { it.value.flatten() })
        var fits = true
        showInEditor {
            editorState.applyRemote(
                split.splices.map { EditorSplice(it.index, it.deleteCount, it.insert) },
                appear = true,
                appearTag = author.orEmpty(),
            )
            // in the same call, so the text of an agent never shows for a frame before it is hidden
            split.chunks.forEach { revealOf(it.clientId).insert(it.position, it.text) }
            // an edit on a wrong base text would change the wrong characters of the document
            if (editorState.text != text) {
                Timber.e("The editor differs from the document after a remote change")
                stopReveals()
                editorState.setText(text)
                fits = false
            }
        }
        if (split.chunks.isNotEmpty()) publishPeers()
        if (!fits) return forgetAuthors()
        splices.forEach { moveAuthors(it.index, it.deleteCount, it.insert.length, author) }
    }

    private fun revealOf(clientId: String): KodeEditorReveal = _agentReveals.value[clientId]
        ?: KodeEditorReveal(editorState, tag = "agent:$clientId").also {
            it.layout = layout
            _agentReveals.value += clientId to it
        }

    /** shows all text of agents that is still hidden */
    private fun stopReveals() = agentReveals.value.values.forEach { it.stop() }

    /** the reveals moved on, so the cursors of the agents move with them */
    fun onRevealsMoved() = publishPeers()

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
        // the reveals of agents that left, once they showed everything
        val reveals = agentReveals.value
        val gone = reveals.filter { (clientId, reveal) -> !reveal.isRevealing && peers.none { it.clientId == clientId } }
        if (gone.isNotEmpty()) _agentReveals.value = reveals - gone.keys
        _authorColors.value = authorColors + peers.associate { it.username to it.color }
        publishPeers()
    }

    /**
     * An agent that types out its text has its cursor at the head of what is shown, and no viewport to jump to. Between
     * its chunks the cursor is at its focus, or at the end of what it wrote when it has none, so it does not go away
     * with each pause (docs/collaboration.md of KodeEditor).
     */
    private fun publishPeers() {
        val reveals = agentReveals.value
        _peers.value = if (reveals.isEmpty()) reportedPeers else reportedPeers.map { peer ->
            val reveal = reveals[peer.clientId] ?: return@map peer
            val at = reveal.head ?: reveal.end?.takeIf { peer.selections.isEmpty() } ?: return@map peer
            peer.copy(selections = listOf(RemoteSelection(at, at)), viewport = null)
        }
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

/** a longer change of an agent shows at once */
internal const val MAX_REVEAL_LENGTH = 2048

/** There is no document open */
private object NoDocument : EditableTextDocument {
    override val text: String? = null
    override val version: DocumentVersion? = null
    override fun applyLocalEdit(base: DocumentVersion, baseText: String, splices: List<TextSplice>) = null
}
