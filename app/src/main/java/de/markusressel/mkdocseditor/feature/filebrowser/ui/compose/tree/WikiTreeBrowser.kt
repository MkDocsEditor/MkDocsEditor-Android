package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.resourceKind

/**
 * Loads a small preview image of a resource, or null when there is none
 */
fun interface ThumbnailLoader {
    suspend fun load(resource: ResourceData): ImageBitmap?
}

/**
 * Browses the wiki folder by folder, or searches it, for the pickers and the move dialog (like
 * `TreeBrowser.vue` of the web client). Folders open on click; other items are picked.
 *
 * @param sectionId the open folder
 * @param showDocuments list documents besides folders
 * @param showResources list resources besides folders
 * @param filter further filter for documents and resources, for example images only
 * @param pickSections folders can be picked too (a link to a folder), with a button next to them
 * @param disabledIds items that cannot be picked or opened (a folder cannot move into itself)
 */
@Composable
fun WikiTreeBrowser(
    tree: WikiTree,
    sectionId: String,
    onSectionChange: (String) -> Unit,
    onPick: (WikiItem) -> Unit,
    modifier: Modifier = Modifier,
    showDocuments: Boolean = true,
    showResources: Boolean = true,
    filter: (WikiItem) -> Boolean = { true },
    pickSections: Boolean = false,
    pickCurrentLabel: String = stringResource(R.string.tree_browser_this_folder),
    disabledIds: Set<String> = emptySet(),
    searchPlaceholder: String = stringResource(R.string.tree_browser_search),
    thumbnailLoader: ThumbnailLoader? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val current = tree.section(sectionId) ?: tree.root

    fun allowed(item: WikiItem): Boolean = when (item) {
        is WikiItem.Section -> true
        is WikiItem.Document -> showDocuments && filter(item)
        is WikiItem.Resource -> showResources && filter(item)
    }

    fun activate(item: WikiItem) {
        if (item.id in disabledIds) return
        if (item is WikiItem.Section) {
            onSectionChange(item.id)
            query = ""
        } else {
            onPick(item)
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("browser-search"),
            placeholder = { Text(searchPlaceholder) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.tree_browser_clear))
                    }
                }
            },
            singleLine = true,
        )

        if (query.isBlank()) {
            val ancestors = tree.ancestorsAndSelf(current).dropLast(1)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { ancestors.lastOrNull()?.let { onSectionChange(it.id) } },
                    enabled = ancestors.isNotEmpty(),
                    modifier = Modifier.testTag("browser-up"),
                ) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.tree_browser_up))
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ancestors.forEach { section ->
                        TextButton(onClick = { onSectionChange(section.id) }) {
                            Text(crumbTitle(tree, section), maxLines = 1)
                        }
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        text = crumbTitle(tree, current),
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .testTag("browser-current"),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                if (pickSections && current.id !in disabledIds) {
                    TextButton(
                        onClick = { onPick(WikiItem.Section(current)) },
                        modifier = Modifier.testTag("browser-pick-current"),
                    ) {
                        Text(pickCurrentLabel)
                    }
                }
            }

            val entries = (current.subsections.sortedBy { it.name.lowercase() }.map { WikiItem.Section(it) } +
                current.documents.sortedBy { it.name.lowercase() }.map { WikiItem.Document(it) } +
                current.resources.sortedBy { it.name.lowercase() }.map { WikiItem.Resource(it) })
                .filter(::allowed)

            LazyColumn(modifier = Modifier.testTag("browser-list")) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.tree_browser_empty),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(entries, key = { it.id }) { item ->
                    BrowserEntry(
                        item = item,
                        subtitle = (item as? WikiItem.Resource)?.resource?.filesize?.let(::formatSize),
                        disabled = item.id in disabledIds,
                        pickSection = pickSections && item is WikiItem.Section,
                        thumbnailLoader = thumbnailLoader,
                        onClick = { activate(item) },
                        onPickSection = { onPick(item) },
                    )
                }
            }
        } else {
            val hits = tree.search(query).filter { allowed(it.item) }
            LazyColumn(modifier = Modifier.testTag("browser-hits")) {
                if (hits.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.tree_browser_no_hits),
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(hits, key = { it.item.id }) { hit ->
                    val wiki = stringResource(R.string.tree_browser_wiki)
                    BrowserEntry(
                        item = hit.item,
                        subtitle = hit.folders.joinToString(" › ").ifEmpty { wiki },
                        disabled = hit.item.id in disabledIds,
                        pickSection = false,
                        thumbnailLoader = thumbnailLoader,
                        onClick = {
                            // a folder found by the search is picked when folders can be picked
                            if (pickSections && hit.item is WikiItem.Section && hit.item.id !in disabledIds) {
                                onPick(hit.item)
                            } else {
                                activate(hit.item)
                            }
                        },
                        onPickSection = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun crumbTitle(tree: WikiTree, section: SectionData): String =
    if (section.id == tree.root.id) stringResource(R.string.tree_browser_wiki) else section.name

@Composable
private fun BrowserEntry(
    item: WikiItem,
    subtitle: String?,
    disabled: Boolean,
    pickSection: Boolean,
    thumbnailLoader: ThumbnailLoader?,
    onClick: () -> Unit,
    onPickSection: () -> Unit,
) {
    val tag = when (item) {
        is WikiItem.Section -> "browser-section"
        is WikiItem.Document -> "browser-document"
        is WikiItem.Resource -> "browser-resource"
    }
    ListItem(
        modifier = Modifier
            .testTag(tag)
            .clickable(enabled = !disabled, onClick = onClick),
        headlineContent = {
            Text(
                text = item.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (disabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { ItemIcon(item, thumbnailLoader) },
        trailingContent = if (pickSection) {
            {
                IconButton(onClick = onPickSection, enabled = !disabled) {
                    Icon(Icons.Outlined.Link, contentDescription = stringResource(R.string.tree_browser_pick_folder, item.name))
                }
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(),
    )
}

@Composable
private fun ItemIcon(item: WikiItem, thumbnailLoader: ThumbnailLoader?) {
    val resource = (item as? WikiItem.Resource)?.resource
    if (resource != null && thumbnailLoader != null && resource.resourceKind() == ResourceKind.IMAGE) {
        var bitmap by remember(resource.id) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(resource.id, resource.modtime) { bitmap = thumbnailLoader.load(resource) }
        bitmap?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
            return
        }
    }
    Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = when (item) {
                is WikiItem.Section -> Icons.Outlined.Folder
                is WikiItem.Document -> Icons.Outlined.Description
                is WikiItem.Resource -> if (item.resource.resourceKind() == ResourceKind.IMAGE) Icons.Outlined.Image else Icons.AutoMirrored.Outlined.InsertDriveFile
            },
            contentDescription = null,
            tint = when (item) {
                is WikiItem.Section -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** a file size like `12 KB` */
fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(java.util.Locale.ENGLISH, bytes / (1024.0 * 1024.0))
}
