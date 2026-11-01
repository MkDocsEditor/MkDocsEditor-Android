package de.markusressel.mkdocseditor.feature.editor.ui.compose.picker

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.domain.PickerMarkdown
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.resourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.ThumbnailLoader
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.formatSize
import de.markusressel.mkdocsrestclient.reference.BacklinkModel
import de.markusressel.mkdocsrestclient.reference.ItemType
import de.markusressel.mkdocsrestclient.reference.ReferenceModel

/** the panel's data; a list is null while it loads */
internal data class PageReferences(
    val references: List<ReferenceModel>? = null,
    val backlinks: List<BacklinkModel>? = null,
    val referencesError: PageReferencesError? = null,
    val backlinksError: PageReferencesError? = null,
)

internal enum class PageReferencesError(@StringRes val text: Int) {
    UNSUPPORTED(R.string.page_references_unsupported),
    OFFLINE(R.string.page_references_offline),
}

/**
 * What the open page uses and what uses it:
 * - Resources: the files the page uses and the other files of its folder, to insert again,
 *   plus uploads into the folder
 * - Links: every reference of the page in text order, broken ones highlighted; a tap selects it
 *   in the editor, and references to moved items can be fixed
 * - Used by: the pages that link here; a tap opens them
 */
@Composable
internal fun PageReferencesDialog(
    tree: WikiTree,
    documentId: String,
    state: PageReferences,
    thumbnailLoader: ThumbnailLoader?,
    onInsert: (String) -> Unit,
    onJump: (ReferenceModel) -> Unit,
    onFix: (ReferenceModel) -> Unit,
    onOpenDocument: (String) -> Unit,
    onUpload: () -> Unit,
    onDismiss: () -> Unit,
) {
    val document = tree.documentById(documentId) ?: return
    val fromPath = tree.pathOf(WikiItem.Document(document))
    val folder = tree.parentOfDocument(documentId) ?: tree.root
    var tab by rememberSaveable { mutableStateOf(TAB_RESOURCES) }
    val broken = state.references?.count { it.status == STATUS_BROKEN } ?: 0

    PickerScaffold(
        title = stringResource(R.string.page_references_title),
        tabs = listOf(
            TAB_RESOURCES to stringResource(R.string.page_references_tab_resources),
            TAB_LINKS to if (broken > 0) {
                pluralStringResource(R.plurals.page_references_tab_links_broken, broken, broken)
            } else {
                stringResource(R.string.page_references_tab_links)
            },
            TAB_USED_BY to stringResource(R.string.page_references_tab_used_by),
        ),
        selectedTab = tab,
        onTabSelected = { tab = it },
        onDismiss = onDismiss,
    ) {
        when (tab) {
            TAB_RESOURCES -> LazyColumn(modifier = Modifier.testTag("references-resources")) {
                item {
                    OutlinedButton(
                        onClick = onUpload,
                        modifier = Modifier
                            .padding(16.dp)
                            .testTag("references-upload"),
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null)
                        Text(
                            text = stringResource(R.string.page_references_upload, tree.pathOf(folder).ifEmpty { "/" }),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                resourcesContent(
                    tree = tree,
                    state = state,
                    folderResources = folder.resources,
                    thumbnailLoader = thumbnailLoader,
                    onInsert = { onInsert(PickerMarkdown.markdownLink(tree, fromPath, it)) },
                    onJump = onJump,
                    onFix = onFix,
                )
            }

            TAB_LINKS -> LazyColumn(modifier = Modifier.testTag("references-links")) {
                listState(state.references, state.referencesError, R.string.page_references_no_links) { references ->
                    items(references) { reference ->
                        ReferenceEntry(reference, onClick = { onJump(reference) }, onFix = { onFix(reference) })
                    }
                }
            }

            TAB_USED_BY -> LazyColumn(modifier = Modifier.testTag("references-used-by")) {
                listState(state.backlinks, state.backlinksError, R.string.backlinks_none) { backlinks ->
                    items(backlinks) { backlink ->
                        ListItem(
                            modifier = Modifier
                                .testTag("references-backlink")
                                .clickable { onOpenDocument(backlink.source.id) },
                            leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                            headlineContent = { Text(backlink.source.path, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.backlinks_line, backlink.line, backlink.destination),
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/** a loading indicator, an error, an empty text or the [content] */
private fun <T> LazyListScope.listState(
    list: List<T>?,
    error: PageReferencesError?,
    emptyText: Int,
    content: LazyListScope.(List<T>) -> Unit,
) {
    when {
        error != null -> item { Message(stringResource(error.text), isError = true) }
        list == null -> item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }
        list.isEmpty() -> item { Message(stringResource(emptyText)) }
        else -> content(list)
    }
}

private fun LazyListScope.resourcesContent(
    tree: WikiTree,
    state: PageReferences,
    folderResources: List<ResourceData>,
    thumbnailLoader: ThumbnailLoader?,
    onInsert: (WikiItem.Resource) -> Unit,
    onJump: (ReferenceModel) -> Unit,
    onFix: (ReferenceModel) -> Unit,
) {
    val references = state.references.orEmpty()
    // the first reference to each resource the page uses, in text order
    val used = references
        .filter { it.target?.type == ItemType.RESOURCE }
        .distinctBy { it.target!!.id }
        .mapNotNull { reference -> tree.resourceById(reference.target!!.id)?.let { it to reference } }
    val brokenImages = references.filter { it.status == STATUS_BROKEN && it.kind == KIND_IMAGE }
    val usedIds = used.map { it.first.id }.toSet()
    val others = folderResources.filter { it.id !in usedIds }.sortedBy { it.name.lowercase() }

    item { Header(stringResource(R.string.page_references_used_here)) }
    when {
        state.referencesError != null -> item { Message(stringResource(state.referencesError.text), isError = true) }
        state.references == null -> item {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            }
        }
        used.isEmpty() && brokenImages.isEmpty() -> item { Message(stringResource(R.string.page_references_none_used)) }
    }
    items(brokenImages, key = { "broken-${it.line}-${it.column}" }) { reference ->
        ReferenceEntry(reference, onClick = { onJump(reference) }, onFix = { onFix(reference) })
    }
    items(used, key = { "used-" + it.first.id }) { (resource, reference) ->
        ResourceEntry(
            tree = tree,
            resource = resource,
            thumbnailLoader = thumbnailLoader,
            onClick = { onInsert(WikiItem.Resource(resource)) },
            onFind = { onJump(reference) },
        )
    }

    item { Header(stringResource(R.string.page_references_in_folder)) }
    if (others.isEmpty()) {
        item { Message(stringResource(R.string.page_references_folder_empty)) }
    }
    items(others, key = { "folder-" + it.id }) { resource ->
        ResourceEntry(
            tree = tree,
            resource = resource,
            thumbnailLoader = thumbnailLoader,
            onClick = { onInsert(WikiItem.Resource(resource)) },
            onFind = null,
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Message(text: String, isError: Boolean = false) {
    Text(
        text = text,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(if (isError) "references-error" else "references-message"),
    )
}

@Composable
private fun ResourceEntry(
    tree: WikiTree,
    resource: ResourceData,
    thumbnailLoader: ThumbnailLoader?,
    onClick: () -> Unit,
    onFind: (() -> Unit)?,
) {
    val image = resource.resourceKind() == ResourceKind.IMAGE
    val folder = tree.parentOfResource(resource.id)
    val path = folder?.let { tree.pathOf(it) }.orEmpty()
    ListItem(
        modifier = Modifier
            .testTag(if (onFind != null) "references-used-resource" else "references-folder-resource")
            .clickable(onClick = onClick),
        leadingContent = {
            if (image && thumbnailLoader != null) {
                Thumbnail(resource, thumbnailLoader)
            } else {
                Icon(if (image) Icons.Outlined.Image else Icons.Outlined.InsertDriveFile, contentDescription = null)
            }
        },
        headlineContent = { Text(resource.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            val size = resource.filesize.takeIf { it > 0 }?.let(::formatSize)
            Text(listOfNotNull(path.ifEmpty { null }, size).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = onFind?.let {
            {
                IconButton(onClick = it, modifier = Modifier.testTag("references-find")) {
                    Icon(Icons.Outlined.Search, contentDescription = stringResource(R.string.page_references_find, resource.name))
                }
            }
        },
    )
}

@Composable
private fun Thumbnail(resource: ResourceData, thumbnailLoader: ThumbnailLoader) {
    var bitmap by remember(resource.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(resource.id) { bitmap = thumbnailLoader.load(resource) }
    val current = bitmap
    if (current != null) {
        Image(bitmap = current, contentDescription = null, modifier = Modifier.size(40.dp), contentScale = ContentScale.Crop)
    } else {
        Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun ReferenceEntry(reference: ReferenceModel, onClick: () -> Unit, onFix: (() -> Unit)? = null) {
    val broken = reference.status == STATUS_BROKEN
    val icon = when {
        broken && reference.kind == KIND_IMAGE -> Icons.Outlined.BrokenImage
        broken -> Icons.Outlined.LinkOff
        reference.kind == KIND_IMAGE -> Icons.Outlined.Image
        reference.target?.type == ItemType.SECTION -> Icons.Outlined.Folder
        reference.target?.type == ItemType.RESOURCE -> Icons.Outlined.InsertDriveFile
        else -> Icons.Outlined.Link
    }
    val color = if (broken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val detail = if (broken) {
        when (reference.reason) {
            "outside-docs" -> stringResource(R.string.page_references_broken_outside, reference.line)
            else -> reference.movedTo?.let {
                stringResource(R.string.page_references_broken_moved, reference.line, it.path)
            } ?: stringResource(R.string.page_references_broken_missing, reference.line, reference.path ?: reference.destination)
        }
    } else {
        stringResource(R.string.page_references_target, reference.line, reference.target?.path?.ifEmpty { "/" } ?: "")
    }
    ListItem(
        modifier = Modifier
            .testTag(if (broken) "references-broken" else "references-ok")
            .clickable(onClick = onClick),
        leadingContent = { Icon(icon, contentDescription = null, tint = color) },
        headlineContent = {
            Text(
                text = reference.destination,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = { Text(detail, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis) },
        trailingContent = if (onFix != null && reference.suggestedDestination != null) {
            {
                TextButton(onClick = onFix, modifier = Modifier.testTag("references-fix")) {
                    Text(stringResource(R.string.page_references_fix))
                }
            }
        } else null,
    )
}

private const val TAB_RESOURCES = "resources"
private const val TAB_LINKS = "links"
private const val TAB_USED_BY = "used-by"

private const val STATUS_BROKEN = "broken"
private const val KIND_IMAGE = "image"
