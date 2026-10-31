package de.markusressel.mkdocseditor.feature.auth.onboarding.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.Problem
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.ConnectLinkViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun ConnectLinkScreenContent(
    uiState: ConnectLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.connect_title),
                canGoBack = true,
                onBackClicked = onBackClicked,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.loading || uiState.step == Step.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            when (uiState.step) {
                Step.Loading -> Unit

                Step.Confirm, Step.Done -> {
                    Text(text = uiState.serverName, style = MaterialTheme.typography.headlineSmall)
                    Text(text = stringResource(R.string.connect_confirm, uiState.username), style = MaterialTheme.typography.bodyLarge)
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.loading && uiState.step == Step.Confirm,
                        onClick = { onUiEvent(UiEvent.ConnectClicked) },
                    ) {
                        Text(stringResource(R.string.connect_button))
                    }
                    TextButton(enabled = !uiState.loading, onClick = { onUiEvent(UiEvent.SignInManuallyClicked) }) {
                        Text(stringResource(R.string.connect_sign_in_manually))
                    }
                }

                Step.Problem -> {
                    val problem = uiState.problem ?: Problem.Invalid
                    Text(text = stringResource(problem.message), style = MaterialTheme.typography.bodyLarge)
                    if (problem.canRetry) {
                        Button(onClick = { onUiEvent(UiEvent.RetryClicked) }) {
                            Text(stringResource(R.string.connect_retry))
                        }
                    }
                    if (problem.canSignInManually) {
                        OutlinedButton(onClick = { onUiEvent(UiEvent.SignInManuallyClicked) }) {
                            Text(stringResource(R.string.connect_sign_in_manually))
                        }
                    }
                    TextButton(onClick = { onUiEvent(UiEvent.DoneClicked) }) {
                        Text(stringResource(R.string.account_cancel))
                    }
                }
            }

            uiState.error?.let {
                Text(text = it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@CombinedPreview
@Composable
private fun ConnectLinkScreenContentPreview() {
    MkDocsEditorTheme {
        ConnectLinkScreenContent(
            uiState = ConnectLinkViewModel.UiState(step = Step.Confirm, serverName = "Markus' Wiki", username = "markus"),
            onUiEvent = {},
            onBackClicked = {},
        )
    }
}
