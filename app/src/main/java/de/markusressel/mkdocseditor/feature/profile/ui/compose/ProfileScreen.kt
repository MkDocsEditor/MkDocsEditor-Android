package de.markusressel.mkdocseditor.feature.profile.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.profile.ui.ProfileViewModel

object ProfileScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow

        val viewModel = getViewModel<ProfileViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        ProfileScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}
