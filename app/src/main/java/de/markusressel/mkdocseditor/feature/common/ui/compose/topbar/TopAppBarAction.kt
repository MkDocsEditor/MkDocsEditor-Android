package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

sealed interface TopAppBarAction {
    data object ShowInBrowserAction : TopAppBarAction
}