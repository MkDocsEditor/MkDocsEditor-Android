package de.markusressel.mkdocseditor.feature.editor.ui

internal sealed class CodeEditorEvent {
    data class InitialText(val text: String) : CodeEditorEvent()
    data class Error(
        val message: String? = null,
        val throwable: Throwable? = null
    ) : CodeEditorEvent()
}