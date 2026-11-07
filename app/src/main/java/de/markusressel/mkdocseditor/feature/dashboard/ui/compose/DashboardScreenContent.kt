package de.markusressel.mkdocseditor.feature.dashboard.ui.compose

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
import de.markusressel.mkdocseditor.feature.avatar.ui.compose.UserAvatar
import de.markusressel.mkdocseditor.feature.common.ui.compose.TabContentScaffold
import de.markusressel.mkdocseditor.feature.common.ui.compose.syncStatusText
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.MkDocsEditorTopAppBar
import de.markusressel.mkdocseditor.feature.common.ui.compose.topbar.TopAppBarAction
import de.markusressel.mkdocseditor.feature.dashboard.ui.DashboardViewModel.HomePageState
import de.markusressel.mkdocseditor.feature.dashboard.ui.DashboardViewModel.UiEvent
import de.markusressel.mkdocseditor.feature.dashboard.ui.DashboardViewModel.UiState
import de.markusressel.mkdocseditor.feature.editor.ui.compose.parsePeerColor
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.MarkdownPreview
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.PreviewResourceLoader
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import de.markusressel.mkdocsrestclient.dashboard.FeedAuthorModel
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedKind
import de.markusressel.mkdocsrestclient.dashboard.FeedSummaryModel
import de.markusressel.mkdocsrestclient.dashboard.PinModel
import de.markusressel.mkdocsrestclient.dashboard.PinScope
import java.util.Date

/** from this width the home page and the lists stand side by side */
private const val TWO_COLUMNS_DP = 840

/** the color of `:disk` and `:server` on the backend */
private val NEUTRAL_AUTHOR_COLOR = Color(0xFF868E96)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreenContent(
    uiState: UiState,
    onUiEvent: (UiEvent) -> Unit,
    previewResourceLoader: PreviewResourceLoader = PreviewResourceLoader { null },
) {
    TabContentScaffold(
        topBar = {
            MkDocsEditorTopAppBar(
                canGoBack = false,
                title = stringResource(R.string.screen_dashboard_title),
                subtitle = syncStatusText(uiState.lastSync, uiState.lastSyncFailed),
                actions = listOf(TopAppBarAction.FileBrowser.Search, TopAppBarAction.FileBrowser.Profile),
                onActionClicked = { onUiEvent(UiEvent.TopAppBarActionClicked(it)) },
            )
        },
    ) { paddingValues ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            isRefreshing = uiState.loading,
            onRefresh = { onUiEvent(UiEvent.Refresh) },
        ) {
            // the tab scrolls as a whole in both layouts, so pull-to-refresh only starts at its top; the home page
            // grows with its content and never scrolls itself
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (maxWidth >= TWO_COLUMNS_DP.dp) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        HomePageCard(
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp),
                            state = uiState.homePage,
                            previewResourceLoader = previewResourceLoader,
                            onUiEvent = onUiEvent,
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(8.dp),
                        ) {
                            PinsAndFeed(uiState, onUiEvent)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                    ) {
                        HomePageCard(
                            modifier = Modifier.fillMaxWidth(),
                            state = uiState.homePage,
                            previewResourceLoader = previewResourceLoader,
                            onUiEvent = onUiEvent,
                        )
                        PinsAndFeed(uiState, onUiEvent)
                    }
                }
            }
        }
    }
}

/**
 * The card is as high as the page, so the tab around it scrolls, not the page
 */
@Composable
private fun HomePageCard(
    state: HomePageState?,
    previewResourceLoader: PreviewResourceLoader,
    onUiEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    state ?: return
    ElevatedCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.dashboard_home_page),
                style = MaterialTheme.typography.titleMedium,
            )
            val documentId = when (state) {
                is HomePageState.Shown -> state.documentId
                is HomePageState.Unavailable -> state.documentId
                HomePageState.Missing -> null
            }
            if (documentId != null) {
                IconButton(onClick = { onUiEvent(UiEvent.DocumentClicked(documentId)) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.dashboard_home_page_edit))
                }
            }
        }
        when (state) {
            HomePageState.Missing -> Hint(R.string.dashboard_home_page_missing)
            is HomePageState.Unavailable -> Hint(R.string.dashboard_home_page_unavailable)
            is HomePageState.Shown -> {
                var contentHeight by remember { mutableIntStateOf(0) }
                MarkdownPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(contentHeight.coerceAtLeast(MIN_PAGE_HEIGHT_DP).dp),
                    text = state.text,
                    sectionPath = "",
                    resourceLoader = previewResourceLoader,
                    onLinkClicked = { onUiEvent(UiEvent.HomePageLinkClicked(it)) },
                    onContentHeight = { contentHeight = it },
                )
            }
        }
    }
}

private const val MIN_PAGE_HEIGHT_DP = 48

@Composable
private fun ColumnScope.PinsAndFeed(uiState: UiState, onUiEvent: (UiEvent) -> Unit) {
    if (uiState.pinsSupported) {
        if (uiState.familyPins.isEmpty() && uiState.personalPins.isEmpty()) {
            SectionTitle(R.string.dashboard_pins_family)
            Hint(R.string.dashboard_pins_empty)
        }
        PinSection(R.string.dashboard_pins_family, PinScope.Family, uiState.familyPins, uiState.mayPinForEveryone, onUiEvent)
        PinSection(R.string.dashboard_pins_personal, PinScope.Personal, uiState.personalPins, mayUnpin = true, onUiEvent)
    }
    if (uiState.feedSupported) {
        SectionTitle(R.string.dashboard_feed_title)
        if (uiState.feed.isEmpty()) Hint(R.string.dashboard_feed_empty)
        val now = remember(uiState.feed) { Date() }
        uiState.feed.forEach { entry ->
            FeedEntry(entry = entry, now = now, onClick = { onUiEvent(UiEvent.FeedEntryClicked(entry)) })
        }
        if (uiState.feedNext != null) {
            TextButton(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !uiState.loadingOlder,
                onClick = { onUiEvent(UiEvent.LoadOlderFeed) },
            ) {
                Text(stringResource(R.string.dashboard_feed_older))
            }
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun ColumnScope.PinSection(
    title: Int,
    scope: PinScope,
    pins: List<PinModel>,
    mayUnpin: Boolean,
    onUiEvent: (UiEvent) -> Unit,
) {
    if (pins.isEmpty()) return
    SectionTitle(title)
    pins.forEach { pin ->
        ListItem(
            modifier = Modifier.clickable { onUiEvent(UiEvent.DocumentClicked(pin.documentId)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
            headlineContent = { Text(pin.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = folderOf(pin.path)?.let { folder ->
                { Text(folder, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            },
            trailingContent = if (mayUnpin) {
                {
                    IconButton(onClick = { onUiEvent(UiEvent.UnpinClicked(scope, pin)) }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.dashboard_unpin))
                    }
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun FeedEntry(entry: FeedEntryModel, now: Date, onClick: () -> Unit) {
    val time = feedTime(entry.time, now) ?: stringResource(R.string.feed_just_now)
    val authors = entry.authors.joinToString(", ") { it.displayName }
    val what = when (entry.kind) {
        FeedKind.CREATED -> stringResource(R.string.feed_created)
        FeedKind.DELETED -> stringResource(R.string.feed_deleted)
        FeedKind.MOVED -> entry.oldPath?.let { stringResource(R.string.feed_moved, it) }
        else -> entry.summary?.let { summary ->
            listOfNotNull(
                summary.headings.takeIf { it.isNotEmpty() }?.let { stringResource(R.string.feed_edited_in, it.joinToString(", ")) },
                stringResource(R.string.feed_lines, summary.linesAdded, summary.linesRemoved),
            ).joinToString(" · ")
        }
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { AuthorAvatar(entry.authors.firstOrNull()) },
        headlineContent = { Text(nameOf(entry.path), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listOf(authors, time).filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                what?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        },
    )
}

@Composable
private fun AuthorAvatar(author: FeedAuthorModel?) {
    Box(modifier = Modifier.size(36.dp)) {
        author ?: return@Box
        UserAvatar(
            modifier = Modifier.size(36.dp),
            name = author.displayName,
            color = parsePeerColor(author.color) ?: NEUTRAL_AUTHOR_COLOR,
            avatar = Avatar.of(author.accountId, author.avatar),
        )
    }
}

@Composable
private fun SectionTitle(title: Int) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
        text = stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Hint(text: Int) {
    Text(
        modifier = Modifier
            .heightIn(min = 24.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * @return how long ago a change was, null for less than a minute. A clock behind the server's must not show a change
 * in the future.
 */
internal fun feedTime(time: Date, now: Date): String? {
    val shown = minOf(time.time, now.time)
    if (now.time - shown < DateUtils.MINUTE_IN_MILLIS) return null
    return DateUtils.getRelativeTimeSpanString(shown, now.time, DateUtils.MINUTE_IN_MILLIS).toString()
}

/** `guide/setup.md` is `setup` */
internal fun nameOf(path: String): String = path.substringAfterLast('/').removeSuffix(".md")

/** `guide/setup.md` is in `guide`, a page in the top folder in none */
internal fun folderOf(path: String): String? = path.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }

@CombinedPreview
@Composable
private fun DashboardScreenContentPreview() {
    val anna = FeedAuthorModel(accountId = "a1", username = "anna", displayName = "Anna", color = "#1971c2")
    MkDocsEditorTheme {
        DashboardScreenContent(
            uiState = UiState(
                homePage = HomePageState.Missing,
                familyPins = listOf(PinModel("d1", "family/shopping.md", "shopping", anna, Date())),
                feed = listOf(
                    FeedEntryModel(
                        id = "f1",
                        time = Date(),
                        kind = FeedKind.EDITED,
                        itemType = "document",
                        documentId = "d1",
                        path = "family/shopping.md",
                        authors = listOf(anna),
                        summary = FeedSummaryModel(linesAdded = 3, linesRemoved = 1, headings = listOf("Groceries")),
                    ),
                ),
                lastSync = Date(),
            ),
            onUiEvent = {},
        )
    }
}
