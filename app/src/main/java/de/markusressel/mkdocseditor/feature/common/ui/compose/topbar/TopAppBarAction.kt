package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

sealed interface TopAppBarAction {
    sealed interface CodeEditor : TopAppBarAction {
        data object ShowInBrowserAction : CodeEditor
        data object TogglePreviewAction : CodeEditor
    }

    sealed interface FileBrowser : TopAppBarAction {
        data object Search : FileBrowser
    }
}