package de.markusressel.mkdocseditor.feature.about.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.about.data.Changelog
import de.markusressel.mkdocseditor.feature.about.data.ChangelogEntry
import de.markusressel.mkdocseditor.feature.about.data.appVersionText
import de.markusressel.mkdocseditor.feature.about.data.readChangelog
import de.markusressel.mkdocseditor.feature.about.data.url
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * The pull requests this build contains, opened by tapping the version in [AboutScreen]
 */
object WhatsNewScreen : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val changelog = remember(context) { context.readChangelog() }
        val version = remember(context) { context.appVersionText() }
        val uriHandler = LocalUriHandler.current

        TabContentScaffold(
            topBar = {
                MkDocsEditorTopAppBar(
                    title = stringResource(R.string.whats_new_title),
                    canGoBack = true,
                    onBackClicked = { navigator.pop() },
                )
            },
        ) { paddingValues ->
            WhatsNewContent(
                version = version,
                changelog = changelog,
                onEntryClick = { uriHandler.openUri(it.url()) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }
}

@Composable
internal fun WhatsNewContent(
    version: String,
    changelog: Changelog,
    onEntryClick: (ChangelogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        item {
            Text(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                text = version,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (changelog.changes.isEmpty()) {
            item {
                Text(
                    modifier = Modifier.padding(16.dp),
                    text = stringResource(R.string.whats_new_empty),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        items(changelog.changes, key = { it.number }) { entry ->
            ListItem(
                modifier = Modifier.clickable { onEntryClick(entry) },
                headlineContent = { Text(entry.title) },
                supportingContent = { Text(stringResource(R.string.whats_new_entry, entry.number, entry.date)) },
            )
        }
    }
}

@CombinedPreview
@Composable
private fun WhatsNewContentPreview() {
    MkDocsEditorTheme {
        WhatsNewContent(
            version = "Version 0.1.0 (1234, release) · 699a5bc",
            changelog = Changelog(
                commit = "699a5bc",
                changes = listOf(
                    ChangelogEntry(195, "Fade in what others type", "2026-11-23"),
                    ChangelogEntry(190, "Show diffs in the release build again", "2026-11-14"),
                ),
            ),
            onEntryClick = {},
        )
    }
}
