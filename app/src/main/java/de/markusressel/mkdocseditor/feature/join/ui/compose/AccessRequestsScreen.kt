package de.markusressel.mkdocseditor.feature.join.ui.compose

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.auth.api.AccessRequestInfo
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.join.ui.AccessRequestsViewModel
import de.markusressel.mkdocseditor.feature.join.ui.AccessRequestsViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

data class AccessRequestsScreen(
    val backendId: Long,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current
        val viewModel = getViewModel<AccessRequestsViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, backendId) {
            viewModel.initialize(backendId)
        }

        // new requests are only announced by a notification
        val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
        LaunchedEffect(Unit) { askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS) }

        AccessRequestsScreenContent(
            uiState = uiState,
            onUiEvent = viewModel::onUiEvent,
            onBackClicked = { navigator.popOrExit(exit) },
        )
    }
}

@Composable
internal fun AccessRequestsScreenContent(
    uiState: AccessRequestsViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
    onBackClicked: () -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.access_requests_title),
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (uiState.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            uiState.error?.let {
                Text(text = it.resolve(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (uiState.requests.isEmpty() && !uiState.loading) {
                Text(text = stringResource(R.string.access_requests_empty), style = MaterialTheme.typography.bodyLarge)
            }
            uiState.requests.forEach { request -> RequestCard(request, uiState.loading, onUiEvent) }
        }
    }

    uiState.approving?.let { request -> ApproveDialog(request, onUiEvent) }
}

@Composable
private fun RequestCard(request: AccessRequestInfo, loading: Boolean, onUiEvent: (UiEvent) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = request.name, style = MaterialTheme.typography.titleMedium)
            request.message?.takeIf { it.isNotBlank() }?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
            request.invitedByName?.let {
                Text(text = stringResource(R.string.access_requests_invited_by, it), style = MaterialTheme.typography.bodySmall)
            }
            request.documentPath?.let {
                Text(text = stringResource(R.string.access_requests_document, it), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !loading, onClick = { onUiEvent(UiEvent.ApproveClicked(request)) }) {
                    Text(stringResource(R.string.access_requests_approve))
                }
                OutlinedButton(enabled = !loading, onClick = { onUiEvent(UiEvent.RejectClicked(request)) }) {
                    Text(stringResource(R.string.access_requests_reject))
                }
            }
        }
    }
}

@Composable
private fun ApproveDialog(request: AccessRequestInfo, onUiEvent: (UiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onUiEvent(UiEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.access_requests_approve_title, request.name)) },
        // anyone who can reach the server can type any name
        text = { Text(stringResource(R.string.access_requests_approve_warning)) },
        confirmButton = {
            TextButton(onClick = { onUiEvent(UiEvent.RoleConfirmed(request, ROLE_EDITOR)) }) {
                Text(stringResource(R.string.access_requests_role_editor))
            }
        },
        dismissButton = {
            TextButton(onClick = { onUiEvent(UiEvent.RoleConfirmed(request, ROLE_READER)) }) {
                Text(stringResource(R.string.access_requests_role_reader))
            }
        },
    )
}

private const val ROLE_EDITOR = "editor"
private const val ROLE_READER = "reader"

@CombinedPreview
@Composable
private fun AccessRequestsScreenContentPreview() {
    MkDocsEditorTheme {
        AccessRequestsScreenContent(
            uiState = AccessRequestsViewModel.UiState(
                requests = listOf(
                    AccessRequestInfo(
                        id = "1",
                        name = "Anna",
                        message = "Markus sent me the shopping list",
                        status = "pending",
                        documentPath = "Haushalt/Einkauf.md",
                        invitedByName = "Markus",
                    )
                )
            ),
            onUiEvent = {},
            onBackClicked = {},
        )
    }
}
