package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import de.markusressel.mkdocseditor.feature.editor.ui.compose.AuthorHighlightMode
import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionIndicator
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

sealed interface TopAppBarAction {
    sealed interface CodeEditor : TopAppBarAction {
        data object ShowInBrowserAction : CodeEditor
        data object ShareLinkAction : CodeEditor
        /** a menu with [ShareLinkAction] and [ShowInBrowserAction], so that a short title still fits */
        data object MoreAction : CodeEditor
        /** @param mode the arrangement of the edit mode, null while reading */
        data class EditorModeAction(val mode: SplitMode?) : CodeEditor
        data class OnlineUsersAction(val remotePeers: List<RemotePeer>) : CodeEditor
        data class FollowUserAction(val accountId: String) : CodeEditor
        data object PageReferencesAction : CodeEditor
        data class AuthorHighlightAction(val mode: AuthorHighlightMode) : CodeEditor
        data class ConnectionAction(val indicator: ConnectionIndicator) : CodeEditor
    }

    sealed interface FileBrowser : TopAppBarAction {
        data object Search : FileBrowser
        data object Profile : FileBrowser
    }
}