package de.markusressel.mkdocseditor.feature.main.ui

sealed class NavItem {
    sealed class Primary : NavItem()
    sealed class Secondary : NavItem()

    data object BackendSelection : Primary()
    data object FileBrowser : Primary()
    data object Settings : Primary()

    data object About : Secondary()
}
