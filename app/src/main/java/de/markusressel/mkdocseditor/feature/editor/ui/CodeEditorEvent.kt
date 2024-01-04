package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import java.util.LinkedList

internal sealed class CodeEditorEvent {
    data class ConnectionStatus(
        val connected: Boolean,
        val errorCode: Int? = null,
        val throwable: Throwable? = null
    ) : CodeEditorEvent()

    data class InitialText(val text: String) : CodeEditorEvent()

    data class TextChange(
        val newText: String,
        val patches: LinkedList<diff_match_patch.Patch>
    ) : CodeEditorEvent()

    data class Error(
        val message: String? = null,
        val throwable: Throwable? = null
    ) : CodeEditorEvent()
}