package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import de.markusressel.mkdocseditor.feature.editor.ui.ConnectionIndicator
import de.markusressel.mkdocseditor.feature.editor.ui.compose.SplitMode
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer

sealed interface TopAppBarAction {
    sealed interface CodeEditor : TopAppBarAction {
        data object ShowInBrowserAction : CodeEditor
        data object ShareLinkAction : CodeEditor
        /**
         * A menu with the actions that are not in the bar, so that a short title still fits
         *
         * @param entries the actions of the menu, [MenuDivider] between groups
         */
        data class MoreAction(val entries: List<CodeEditor>) : CodeEditor
        data object MenuDivider : CodeEditor
        data object RenameAction : CodeEditor
        data object MoveAction : CodeEditor
        data object PinAction : CodeEditor
        data object DeleteAction : CodeEditor
        /** @param mode the arrangement of the edit mode, null while reading */
        data class EditorModeAction(val mode: SplitMode?) : CodeEditor
        data class OnlineUsersAction(val remotePeers: List<RemotePeer>) : CodeEditor
        data class FollowUserAction(val instanceId: String) : CodeEditor
        data object PageReferencesAction : CodeEditor
        /** the QR code stickers of the page */
        data object QrCodesAction : CodeEditor
        /** opens the sheet with the view settings of the editor */
        data object ViewSettingsAction : CodeEditor
        data class ConnectionAction(val indicator: ConnectionIndicator) : CodeEditor
    }

    sealed interface FileBrowser : TopAppBarAction {
        data object Search : FileBrowser
        data object Profile : FileBrowser
    }
}