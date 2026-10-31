package de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.compose

import android.widget.Toast
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
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.findActivity
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.PasskeySetupEvent
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.main.data.rememberMainNavigationRequests
import kotlinx.coroutines.launch

/**
 * The step after a sign-in: suggest a passkey when this device has none for the account.
 * Closes itself right away when no suggestion is needed.
 */
data class PasskeySetupScreen(
    val backendId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current
        val mainNavigation = rememberMainNavigationRequests()
        val viewModel = getViewModel<PasskeySetupViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, backendId) {
            viewModel.initialize(backendId)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is PasskeySetupEvent.CreatePasskey -> {
                        val activity = context.findActivity() as? ComponentActivity
                        if (activity == null) {
                            viewModel.onUiEvent(UiEvent.PasskeyCreated(null))
                            return@collect
                        }
                        activity.lifecycleScope.launch {
                            try {
                                val json = viewModel.credentialClient.createPasskey(activity, event.requestJson)
                                viewModel.onUiEvent(UiEvent.PasskeyCreated(json))
                            } catch (e: Exception) {
                                viewModel.onUiEvent(UiEvent.PasskeyFailed(e))
                            }
                        }
                    }

                    is PasskeySetupEvent.Finish -> {
                        mainNavigation.showFileBrowser()
                        navigator.popOrExit(exit)
                    }
                }
            }
        }

        PasskeySetupScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            onOpenProviderSettings = {
                val activity = context.findActivity() ?: context
                if (!viewModel.providers.openProviderSettings(activity)) {
                    Toast.makeText(context, R.string.passkey_setup_settings_missing, Toast.LENGTH_LONG).show()
                }
            },
            onOpenStore = { url -> viewModel.providers.openStore(context.findActivity() ?: context, url) },
        )
    }
}
