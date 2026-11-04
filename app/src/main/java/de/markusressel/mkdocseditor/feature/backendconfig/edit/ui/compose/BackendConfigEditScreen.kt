package de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.backendconfig.edit.ui.BackendConfigEditViewModel

data class BackendConfigEditScreen(
    val id: Long,
) : Screen {
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.current

        val viewModel = getViewModel<BackendConfigEditViewModel>()
        LaunchedEffect(viewModel, id) {
            viewModel.initialize(id)
        }

        LaunchedEffect(viewModel.events) {
            viewModel.events.collect { event ->
                when (event) {
                    is BackendConfigEditViewModel.BackendEditEvent.CloseScreen -> {
                        navigator?.pop()
                    }

                    is BackendConfigEditViewModel.BackendEditEvent.OpenLogin -> {
                        val login = LoginScreen(backendId = event.backendId)
                        if (event.replaceScreen) navigator?.replace(login) else navigator?.push(login)
                    }
                }
            }
        }

        val uiState by viewModel.uiState.collectAsState()

        BackendConfigEditScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent
        )
    }
}
