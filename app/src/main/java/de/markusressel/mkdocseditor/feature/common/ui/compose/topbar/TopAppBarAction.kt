package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionIndicator
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

sealed interface TopAppBarAction {
    sealed interface CodeEditor : TopAppBarAction {
        data object ShowInBrowserAction : CodeEditor
        /** @param mode the arrangement of the edit mode, null while reading */
        data class EditorModeAction(val mode: SplitMode?) : CodeEditor
        data class OnlineUsersAction(val remotePeers: List<RemotePeer>) : CodeEditor
        data object PageReferencesAction : CodeEditor
        data class ConnectionAction(val indicator: ConnectionIndicator) : CodeEditor
    }

    sealed interface FileBrowser : TopAppBarAction {
        data object Search : FileBrowser
        data object Profile : FileBrowser
    }
}