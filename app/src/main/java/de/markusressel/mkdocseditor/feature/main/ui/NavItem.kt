package de.markusressel.mkdocseditor.feature.main.ui

sealed class NavItem {
    sealed class Primary : NavItem()
    sealed class Secondary : NavItem()

    object FileBrowser : Primary()
    object Settings : Primary()

    object About : Secondary()
}
