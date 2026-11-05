package de.markusressel.mkdocseditor.feature.join.ui.compose

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.link.ui.compose.AuthLinkScreen
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.JoinEvent
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.join.ui.JoinLinkViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/** Follows a join link, see [JoinLinkViewModel] */
data class JoinLinkScreen(
    val linkText: String,
) : Screen {

    override fun toString() = "JoinLinkScreen"

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current
        val viewModel = getViewModel<JoinLinkViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, linkText) {
            viewModel.initialize(linkText)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    is JoinEvent.OpenLogin -> navigator.replace(LoginScreen(event.backendId))
                    is JoinEvent.OpenInvite -> navigator.replace(AuthLinkScreen(linkText = event.token, backendId = event.backendId))
                    is JoinEvent.Close -> navigator.popOrExit(exit)
                }
            }
        }

        BackHandler { viewModel.onUiEvent(UiEvent.BackClicked) }

        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner, uiState.step) {
            if (uiState.step == Step.Waiting) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.pollRequest() }
            }
        }

        // the answer can take days, and only a notification tells about it
        val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(uiState.step) {
            if (uiState.step == Step.Waiting) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        JoinLinkScreenContent(uiState = uiState, onUiEvent = viewModel::onUiEvent)
    }
}

@Composable
internal fun JoinLinkScreenContent(
    uiState: JoinLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.join_title),
                canGoBack = true,
                onBackClicked = { onUiEvent(UiEvent.BackClicked) },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.loading || uiState.step == Step.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (uiState.step) {
                Step.Loading -> Unit
                Step.Welcome -> Welcome(uiState, onUiEvent)
                Step.Request -> RequestForm(uiState, onUiEvent)
                Step.Waiting -> Waiting(uiState, onUiEvent)
                Step.Problem -> Problem(uiState, onUiEvent)
            }

            uiState.error?.let {
                Text(text = it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ServerHeader(uiState: JoinLinkViewModel.UiState) {
    Text(text = uiState.serverName, style = MaterialTheme.typography.headlineSmall)
    // the name comes from the server, the host is what proves which server this is
    Text(
        text = uiState.serverHost,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Welcome(uiState: JoinLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    ServerHeader(uiState)
    Text(text = stringResource(R.string.join_welcome), style = MaterialTheme.typography.bodyLarge)
    Button(modifier = Modifier.fillMaxWidth(), onClick = { onUiEvent(UiEvent.SignInClicked) }) {
        Text(stringResource(R.string.join_sign_in))
    }
    if (uiState.canRequestAccess) {
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { onUiEvent(UiEvent.RequestAccessClicked) }) {
            Text(stringResource(R.string.join_request_access))
        }
    } else {
        Text(text = stringResource(R.string.join_ask_admin), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RequestForm(uiState: JoinLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    ServerHeader(uiState)
    Text(text = stringResource(R.string.join_request_explanation), style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.name,
        onValueChange = { onUiEvent(UiEvent.NameChanged(it.take(NAME_LIMIT))) },
        label = { Text(stringResource(R.string.join_request_name)) },
        singleLine = true,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = uiState.message,
        onValueChange = { onUiEvent(UiEvent.MessageChanged(it.take(MESSAGE_LIMIT))) },
        label = { Text(stringResource(R.string.join_request_message)) },
        minLines = 2,
    )
    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.loading && uiState.name.isNotBlank(),
        onClick = { onUiEvent(UiEvent.SubmitClicked) },
    ) {
        Text(stringResource(R.string.join_request_send))
    }
}

@Composable
private fun Waiting(uiState: JoinLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    ServerHeader(uiState)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    Text(text = stringResource(R.string.join_waiting), style = MaterialTheme.typography.bodyLarge)
    TextButton(onClick = { onUiEvent(UiEvent.WithdrawClicked) }) {
        Text(stringResource(R.string.join_withdraw))
    }
}

@Composable
private fun Problem(uiState: JoinLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    val problem = uiState.problem ?: JoinLinkViewModel.Problem.Invalid
    Text(text = stringResource(problem.message), style = MaterialTheme.typography.bodyLarge)
    if (problem.canRetry) {
        Button(onClick = { onUiEvent(UiEvent.RetryClicked) }) {
            Text(stringResource(R.string.connect_retry))
        }
    }
    if (problem.canRequestAgain) {
        Button(onClick = { onUiEvent(UiEvent.RequestAccessClicked) }) {
            Text(stringResource(R.string.join_request_access))
        }
    }
}

private const val NAME_LIMIT = 80
private const val MESSAGE_LIMIT = 500

@CombinedPreview
@Composable
private fun JoinLinkScreenContentPreview() {
    MkDocsEditorTheme {
        JoinLinkScreenContent(
            uiState = JoinLinkViewModel.UiState(
                step = Step.Welcome,
                serverName = "Family wiki",
                serverHost = "wiki.example.com",
                canRequestAccess = true,
            ),
            onUiEvent = {},
        )
    }
}
