package de.markusressel.mkdocseditor.feature.main.ui

sealed class NavigationEvent {
    data class NavigateToCodeEditor(val documentId: String) : NavigationEvent()
}
