package de.markusressel.mkdocseditor.feature.auth.link.ui.compose

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.feature.auth.account.ui.compose.AccountScreen
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.findActivity
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.link.ui.AuthLinkViewModel
import de.markusressel.mkdocseditor.feature.main.data.rememberMainNavigationRequests
import kotlinx.coroutines.launch

/**
 * Handles a setup, invite or reset link
 *
 * @param linkText the link URL or token
 * @param backendId the backend the link belongs to, if known
 */
data class AuthLinkScreen(
    val linkText: String,
    val backendId: Long? = null,
) : Screen {

    override fun toString() = "AuthLinkScreen(backendId=$backendId)"

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current
        val mainNavigation = rememberMainNavigationRequests()

        val viewModel = getViewModel<AuthLinkViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, linkText) {
            viewModel.initialize(linkText, backendId)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                val activity = context.findActivity() as? ComponentActivity
                when (event) {
                    is AuthLinkViewModel.LinkEvent.CreatePasskey -> {
                        if (activity == null) {
                            viewModel.onUiEvent(AuthLinkViewModel.UiEvent.PasskeyCreated(null))
                            return@collect
                        }
                        activity.lifecycleScope.launch {
                            try {
                                val response = viewModel.credentialClient.createPasskey(activity, event.requestJson)
                                viewModel.onUiEvent(AuthLinkViewModel.UiEvent.PasskeyCreated(response))
                            } catch (e: Exception) {
                                viewModel.onUiEvent(AuthLinkViewModel.UiEvent.PasskeyFailed(e))
                            }
                        }
                    }

                    is AuthLinkViewModel.LinkEvent.SavePassword -> {
                        activity?.lifecycleScope?.launch {
                            viewModel.credentialClient.savePassword(activity, event.username, event.password)
                        }
                    }

                    is AuthLinkViewModel.LinkEvent.Close -> {
                        if (viewModel.uiState.value.step == AuthLinkViewModel.Step.Done) mainNavigation.showFileBrowser()
                        navigator.popOrExit(exit)
                    }
                    is AuthLinkViewModel.LinkEvent.OpenAccount -> navigator.replace(AccountScreen(event.backendId))
                }
            }
        }

        AuthLinkScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            onBackClicked = { navigator.popOrExit(exit) },
        )
    }
}
