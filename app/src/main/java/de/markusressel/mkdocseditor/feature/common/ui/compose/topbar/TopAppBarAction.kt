package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitOrientation

sealed interface TopAppBarAction {
    sealed interface CodeEditor : TopAppBarAction {
        data object ShowInBrowserAction : CodeEditor
        data class TogglePreviewAction(val orientation: SplitOrientation) : CodeEditor
    }

    sealed interface FileBrowser : TopAppBarAction {
        data object Search : FileBrowser
        data object Profile : FileBrowser
    }
}