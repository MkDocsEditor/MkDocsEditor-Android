package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitOrientation
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

sealed interface TopAppBarAction {
    sealed interface CodeEditor : TopAppBarAction {
        data object ShowInBrowserAction : CodeEditor
        data class TogglePreviewAction(val orientation: SplitOrientation) : CodeEditor
        data class OnlineUsersAction(val remotePeers: List<RemotePeer>) : CodeEditor
    }

    sealed interface FileBrowser : TopAppBarAction {
        data object Search : FileBrowser
        data object Profile : FileBrowser
    }
}