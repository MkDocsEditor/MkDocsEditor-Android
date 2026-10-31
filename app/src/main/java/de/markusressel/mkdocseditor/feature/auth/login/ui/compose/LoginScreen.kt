package de.markusressel.mkdocseditor.feature.auth.login.ui.compose

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
import de.markusressel.mkdocseditor.feature.auth.account.ui.compose.AccountScreen
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.findActivity
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.link.ui.compose.AuthLinkScreen
import de.markusressel.mkdocseditor.feature.auth.login.ui.LoginViewModel
import kotlinx.coroutines.launch

/**
 * Sign in to a backend: password (+ second factor), passkey or a saved password
 */
data class LoginScreen(
    val backendId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current

        val viewModel = getViewModel<LoginViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, backendId) {
            viewModel.initialize(backendId)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                val activity = context.findActivity() as? ComponentActivity
                when (event) {
                    is LoginViewModel.LoginEvent.PickCredential -> {
                        if (activity == null) {
                            viewModel.onUiEvent(LoginViewModel.UiEvent.CredentialPicked(null))
                            return@collect
                        }
                        // the activity scope survives this screen, the sheet belongs to the activity
                        activity.lifecycleScope.launch {
                            try {
                                val credential = viewModel.credentialClient.pickCredential(
                                    activityContext = activity,
                                    passkeyRequestJson = event.passkeyRequestJson,
                                    includePasswords = event.includePasswords,
                                )
                                viewModel.onUiEvent(LoginViewModel.UiEvent.CredentialPicked(credential))
                            } catch (e: Exception) {
                                viewModel.onUiEvent(LoginViewModel.UiEvent.CredentialFailed(e))
                            }
                        }
                    }

                    is LoginViewModel.LoginEvent.SavePassword -> {
                        activity?.lifecycleScope?.launch {
                            viewModel.credentialClient.savePassword(activity, event.username, event.password)
                        }
                    }

                    is LoginViewModel.LoginEvent.SignedIn -> {
                        if (event.secondFactorSetupRequired) {
                            Toast.makeText(context, R.string.auth_second_factor_setup_hint, Toast.LENGTH_LONG).show()
                            navigator.replace(AccountScreen(backendId))
                        } else {
                            navigator.popOrExit(exit)
                        }
                    }

                    is LoginViewModel.LoginEvent.OpenLink -> {
                        navigator.replace(AuthLinkScreen(linkText = event.link.token, backendId = event.backendId))
                    }

                    is LoginViewModel.LoginEvent.Close -> navigator.popOrExit(exit)
                }
            }
        }

        LoginScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            onBackClicked = { navigator.popOrExit(exit) },
        )
    }
}
