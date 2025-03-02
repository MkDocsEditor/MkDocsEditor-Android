package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.event.BusEvent
import de.markusressel.mkdocseditor.event.EventBusManager
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel
import javax.inject.Inject

abstract class HiltScreen : Screen {

    @field:Inject
    internal lateinit var eventBusManager: EventBusManager

}

object SearchScreen : HiltScreen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getViewModel<SearchViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is SearchViewModel.UiAction.NavigateBack -> {
                        navigator.pop()
                    }

                    is SearchViewModel.UiAction.NavigateToDocument -> {
                        navigator.pop()
                        eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToDocument(event.documentId))
                    }

                    is SearchViewModel.UiAction.NavigateToResource -> {
                        navigator.pop()
                        eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToResource(event.resourceId))
                    }

                    is SearchViewModel.UiAction.NavigateToSection -> {
                        navigator.pop()
                        eventBusManager.send(BusEvent.CodeEditorBusEvent.GoToSection(event.sectionId))
                    }
                }
            }
        }

        SearchScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
        )
    }
}