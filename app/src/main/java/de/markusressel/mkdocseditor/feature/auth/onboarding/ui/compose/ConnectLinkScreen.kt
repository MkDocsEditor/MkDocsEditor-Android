package de.markusressel.mkdocseditor.feature.auth.onboarding.ui.compose

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.ConnectLinkEvent
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.compose.PasskeySetupScreen

/**
 * Connect to a server with a connect link of the web client (device pairing)
 */
data class ConnectLinkScreen(
    val linkText: String,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current
        val viewModel = getViewModel<ConnectLinkViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, linkText) {
            viewModel.initialize(linkText)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is ConnectLinkEvent.Connected -> {
                        val state = viewModel.uiState.value
                        Toast.makeText(
                            context,
                            context.getString(R.string.connect_done, state.serverName, state.username),
                            Toast.LENGTH_LONG,
                        ).show()
                        navigator.replace(PasskeySetupScreen(event.backendId))
                    }

                    is ConnectLinkEvent.OpenLogin -> navigator.replace(LoginScreen(event.backendId))
                    is ConnectLinkEvent.Close -> navigator.popOrExit(exit)
                }
            }
        }

        ConnectLinkScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            onBackClicked = { navigator.popOrExit(exit) },
        )
    }

    override fun toString() = "ConnectLinkScreen"
}
