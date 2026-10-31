package de.markusressel.mkdocseditor.feature.auth.onboarding.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.auth.link.ui.compose.AuthLinkScreen
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectServerViewModel.ConnectServerEvent
import de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose.BackendConfigSelectionScreen
import de.markusressel.mkdocseditor.feature.main.data.rememberMainNavigationRequests

/**
 * Add a server by its address
 *
 * @param firstRun true when no server exists yet: the screen is the root of the server tab
 * and gives way to the server list once a server is added
 */
data class ConnectServerScreen(
    val firstRun: Boolean = false,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val mainNavigation = rememberMainNavigationRequests()
        val viewModel = getViewModel<ConnectServerViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                // on the first run, the server list takes this screen's place underneath
                val base = if (firstRun) listOf(BackendConfigSelectionScreen) else navigator.items.dropLast(1)
                when (event) {
                    is ConnectServerEvent.OpenLogin -> navigator.replaceAll(base + LoginScreen(event.backendId))
                    is ConnectServerEvent.OpenConnectLink -> navigator.replaceAll(base + ConnectLinkScreen(event.link))
                    is ConnectServerEvent.OpenAuthLink -> navigator.push(AuthLinkScreen(linkText = event.link, backendId = event.backendId))
                    is ConnectServerEvent.Done -> {
                        navigator.replaceAll(base.ifEmpty { listOf(BackendConfigSelectionScreen) })
                        mainNavigation.showFileBrowser()
                    }
                }
            }
        }

        ConnectServerScreenContent(
            uiState = uiState,
            canGoBack = !firstRun && navigator.canPop,
            onUiEvent = viewModel::onUiEvent,
            onBackClicked = { navigator.pop() },
        )
    }
}
