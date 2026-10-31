package de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.Step
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.ui.PasskeySetupViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

internal const val BITWARDEN_PLAY_URL = "https://play.google.com/store/apps/details?id=com.x8bit.bitwarden"
internal const val BITWARDEN_FDROID_URL = "https://mobileapp.bitwarden.com/fdroid/"

@Composable
internal fun PasskeySetupScreenContent(
    uiState: PasskeySetupViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenStore: (String) -> Unit,
) {
    TabContentScaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.loading || uiState.step == Step.Loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when (uiState.step) {
                Step.Loading -> Unit
                Step.Offer -> Offer(uiState, onUiEvent, onOpenProviderSettings, onOpenStore)
                Step.Done -> Done(uiState, onUiEvent)
            }
            uiState.error?.let {
                Text(text = it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Offer(
    uiState: PasskeySetupViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onOpenProviderSettings: () -> Unit,
    onOpenStore: (String) -> Unit,
) {
    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Text(text = stringResource(R.string.passkey_setup_title), style = MaterialTheme.typography.headlineSmall)
    Text(text = stringResource(R.string.passkey_setup_hint), style = MaterialTheme.typography.bodyLarge)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uiState.bitwardenInstalled) {
                Text(text = stringResource(R.string.passkey_setup_bitwarden_installed), style = MaterialTheme.typography.bodyMedium)
                if (uiState.canOpenProviderSettings) {
                    Text(text = stringResource(R.string.passkey_setup_bitwarden_enable_hint), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onOpenProviderSettings) {
                        Text(stringResource(R.string.passkey_setup_open_settings))
                    }
                }
            } else {
                Text(text = stringResource(R.string.passkey_setup_bitwarden_missing), style = MaterialTheme.typography.bodyMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onOpenStore(BITWARDEN_PLAY_URL) }) { Text(stringResource(R.string.passkey_setup_play_store)) }
                    TextButton(onClick = { onOpenStore(BITWARDEN_FDROID_URL) }) { Text(stringResource(R.string.passkey_setup_fdroid)) }
                }
            }
        }
    }

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.loading,
        onClick = { onUiEvent(UiEvent.CreateClicked) },
    ) {
        Text(stringResource(R.string.passkey_setup_create))
    }
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        enabled = !uiState.loading,
        onClick = { onUiEvent(UiEvent.LaterClicked) },
    ) {
        Text(stringResource(R.string.passkey_setup_later))
    }
}

@Composable
private fun Done(uiState: PasskeySetupViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
    Text(
        text = uiState.provider?.let { stringResource(R.string.passkey_setup_done_with, it) }
            ?: stringResource(R.string.passkey_setup_done),
        style = MaterialTheme.typography.headlineSmall,
    )
    Text(text = stringResource(R.string.passkey_setup_done_hint), style = MaterialTheme.typography.bodyLarge)
    Button(modifier = Modifier.fillMaxWidth(), onClick = { onUiEvent(UiEvent.DoneClicked) }) {
        Text(stringResource(R.string.auth_continue))
    }
}

@CombinedPreview
@Composable
private fun PasskeySetupScreenContentPreview() {
    MkDocsEditorTheme {
        PasskeySetupScreenContent(
            uiState = PasskeySetupViewModel.UiState(step = Step.Offer, bitwardenInstalled = true, canOpenProviderSettings = true),
            onUiEvent = {},
            onOpenProviderSettings = {},
            onOpenStore = {},
        )
    }
}
