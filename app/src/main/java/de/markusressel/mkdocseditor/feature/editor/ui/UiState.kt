package de.markusressel.mkdocseditor.feature.editor.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange

data class UiState(
    /**
     * Indicates whether the CodeEditor is in "edit" mode or not
     */
    val editModeActive: Boolean = true,

    val text: AnnotatedString? = null,
    val selection: TextRange? = null,
)