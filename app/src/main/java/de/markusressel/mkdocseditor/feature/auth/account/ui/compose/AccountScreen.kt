package de.markusressel.mkdocseditor.feature.auth.account.ui.compose

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.AccountEvent
import de.markusressel.mkdocseditor.feature.auth.account.ui.AccountViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.auth.common.data.PickedCredential
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.findActivity
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import kotlinx.coroutines.launch

/**
 * The account of the signed-in user on one backend
 */
data class AccountScreen(
    val backendId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current

        val viewModel = getViewModel<AccountViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, backendId) {
            viewModel.initialize(backendId)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                val activity = context.findActivity() as? ComponentActivity
                when (event) {
                    is AccountEvent.CreatePasskey -> {
                        if (activity == null) {
                            viewModel.onUiEvent(UiEvent.PasskeyResult(null))
                            return@collect
                        }
                        activity.lifecycleScope.launch {
                            try {
                                val json = viewModel.credentialClient.createPasskey(activity, event.requestJson)
                                viewModel.onUiEvent(UiEvent.PasskeyResult(json))
                            } catch (e: Exception) {
                                viewModel.onUiEvent(UiEvent.PasskeyFailed(e))
                            }
                        }
                    }

                    is AccountEvent.GetPasskey -> {
                        if (activity == null) {
                            viewModel.onUiEvent(UiEvent.PasskeyResult(null))
                            return@collect
                        }
                        activity.lifecycleScope.launch {
                            try {
                                val credential = viewModel.credentialClient.pickCredential(
                                    activityContext = activity,
                                    passkeyRequestJson = event.requestJson,
                                    includePasswords = false,
                                )
                                val json = (credential as? PickedCredential.Passkey)?.authenticationResponseJson
                                viewModel.onUiEvent(UiEvent.PasskeyResult(json))
                            } catch (e: Exception) {
                                viewModel.onUiEvent(UiEvent.PasskeyFailed(e))
                            }
                        }
                    }

                    is AccountEvent.SavePassword -> {
                        activity?.lifecycleScope?.launch {
                            viewModel.credentialClient.savePassword(activity, event.username, event.password)
                        }
                    }

                    is AccountEvent.SignedOut -> {
                        Toast.makeText(context, R.string.account_signed_out, Toast.LENGTH_SHORT).show()
                        navigator.popOrExit(exit)
                    }

                    is AccountEvent.LoginRequired -> navigator.replace(LoginScreen(event.backendId))
                }
            }
        }

        AccountScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            onBackClicked = { navigator.popOrExit(exit) },
            onOpenUri = { uri ->
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
                    true
                } catch (e: ActivityNotFoundException) {
                    false
                }
            },
            onShareText = { text ->
                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                context.startActivity(Intent.createChooser(intent, null))
            },
        )
    }
}
