package de.markusressel.mkdocseditor.feature.qr.ui.compose

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.hilt.getViewModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.popOrExit
import de.markusressel.mkdocseditor.feature.auth.login.ui.compose.LoginScreen
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.qr.domain.QrPage
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.QrLinkEvent
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.Step
import de.markusressel.mkdocseditor.feature.qr.ui.QrLinkViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/** Follows the URL of a QR code sticker, see [QrLinkViewModel] */
data class QrLinkScreen(
    val linkText: String,
) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val exit = LocalAuthFlowExit.current
        val viewModel = getViewModel<QrLinkViewModel>()
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(viewModel, linkText) {
            viewModel.initialize(linkText)
        }

        LaunchedEffect(viewModel) {
            viewModel.events.collect { event ->
                when (event) {
                    // the sticker is scanned again after signing in, the login has no way back here
                    is QrLinkEvent.OpenLogin -> navigator.replace(LoginScreen(event.backendId))
                    is QrLinkEvent.Close -> navigator.popOrExit(exit)
                }
            }
        }

        BackHandler { viewModel.onUiEvent(UiEvent.BackClicked) }

        QrLinkScreenContent(uiState = uiState, onUiEvent = viewModel::onUiEvent)
    }
}

@Composable
internal fun QrLinkScreenContent(
    uiState: QrLinkViewModel.UiState,
    onUiEvent: (UiEvent) -> Unit,
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                title = stringResource(R.string.qr_link_title, uiState.key),
                canGoBack = true,
                onBackClicked = { onUiEvent(UiEvent.BackClicked) },
            )
        },
    ) { paddingValues ->
        if (uiState.step == Step.PickPage) {
            PagePicker(uiState, onUiEvent, paddingValues)
            return@TabContentScaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (uiState.step) {
                Step.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Step.Conflict -> Conflict(uiState, onUiEvent)
                Step.NoPage -> NoPage(uiState, onUiEvent)
                Step.Problem -> Problem(uiState, onUiEvent)
                Step.PickPage -> Unit
            }
        }
    }
}

@Composable
private fun Conflict(uiState: QrLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    Text(text = stringResource(R.string.qr_link_conflict), style = MaterialTheme.typography.bodyLarge)
    Column {
        uiState.pages.forEach { page -> PageRow(page) { onUiEvent(UiEvent.PageClicked(page.documentId)) } }
    }
}

@Composable
private fun NoPage(uiState: QrLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    val lastPath = uiState.lastPath
    Text(
        text = when {
            lastPath == null -> stringResource(R.string.qr_link_unknown)
            uiState.label != null -> stringResource(R.string.qr_link_gone_with_label, lastPath, uiState.label)
            else -> stringResource(R.string.qr_link_gone, lastPath)
        },
        style = MaterialTheme.typography.bodyLarge,
    )
    if (uiState.canPutOnPage) {
        Button(modifier = Modifier.testTag("qr-put-on-page"), onClick = { onUiEvent(UiEvent.PutOnPageClicked) }) {
            Text(stringResource(R.string.qr_link_put_on_page))
        }
    } else {
        Text(text = stringResource(R.string.qr_link_ask_editor), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Problem(uiState: QrLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit) {
    val problem = uiState.problem ?: QrLinkViewModel.Problem.Invalid
    Text(text = stringResource(problem.message, uiState.serverName), style = MaterialTheme.typography.bodyLarge)
    uiState.failure?.takeIf { problem == QrLinkViewModel.Problem.Failed }?.let {
        Text(text = stringResource(it), style = MaterialTheme.typography.bodyMedium)
    }
    if (problem.canSignIn) {
        Button(onClick = { onUiEvent(UiEvent.SignInClicked) }) {
            Text(stringResource(R.string.join_sign_in))
        }
    }
    if (problem.canRetry) {
        Button(onClick = { onUiEvent(UiEvent.RetryClicked) }) {
            Text(stringResource(R.string.retry))
        }
    }
}

/** the page to put the key on */
@Composable
private fun PagePicker(uiState: QrLinkViewModel.UiState, onUiEvent: (UiEvent) -> Unit, paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .imePadding(),
    ) {
        Text(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
            text = stringResource(R.string.qr_link_pick_page),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("qr-page-query"),
            value = uiState.query,
            onValueChange = { onUiEvent(UiEvent.QueryChanged(it)) },
            label = { Text(stringResource(R.string.qr_link_page_query)) },
            singleLine = true,
        )
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(uiState.shownPages, key = { it.documentId }) { page ->
                PageRow(page) { onUiEvent(UiEvent.TargetPageClicked(page.documentId)) }
            }
        }
    }
}

@Composable
private fun PageRow(page: QrPage, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(page.title) },
        supportingContent = { Text(page.path) },
    )
}

@CombinedPreview
@Composable
private fun QrLinkScreenContentPreview() {
    MkDocsEditorTheme {
        QrLinkScreenContent(
            uiState = QrLinkViewModel.UiState(
                step = Step.Conflict,
                key = "waschmaschine",
                pages = listOf(
                    QrPage("a", "Waschmaschine", "haus/waschmaschine.md"),
                    QrPage("b", "Keller", "haus/keller.md"),
                ),
            ),
            onUiEvent = {},
        )
    }
}
