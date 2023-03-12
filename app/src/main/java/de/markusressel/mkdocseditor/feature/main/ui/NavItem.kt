package de.markusressel.mkdocseditor.feature.main.ui

sealed class NavItem {
    object FileBrowser : NavItem()
    object Settings : NavItem()
    object About : NavItem()
}
