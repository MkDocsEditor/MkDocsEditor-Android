package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.search.ui.SearchViewModel

object SearchScreen : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val viewModel = getViewModel<SearchViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        SearchScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
        )
    }
}