package de.markusressel.mkdocseditor.feature.editor.ui.compose.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.domain.Heading
import de.markusressel.mkdocseditor.feature.editor.domain.Headings
import de.markusressel.mkdocseditor.feature.editor.domain.PickerMarkdown
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.ThumbnailLoader
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.WikiTreeBrowser

/**
 * Loads the text of a document, or null when it can't be loaded
 */
fun interface DocumentTextLoader {
    suspend fun load(documentId: String): String?
}

/**
 * Picks a link target: a page (optionally one of its headings), a folder or a file anywhere in
 * the wiki, or a web address. Inserts the Markdown with the right relative path from the page
 * (like `LinkPickerDialog.vue` of the web client).
 *
 * @param documentId the page the link goes into
 * @param documentText its current text (for links to its own headings)
 * @param selection the selected text, which becomes the link text
 */
@Composable
internal fun LinkPickerDialog(
    tree: WikiTree,
    documentId: String,
    documentText: String,
    selection: String,
    textLoader: DocumentTextLoader,
    thumbnailLoader: ThumbnailLoader?,
    onInsert: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val fromWeb = PickerMarkdown.isWebAddress(selection)
    var tab by rememberSaveable { mutableStateOf(if (fromWeb) TAB_WEB else TAB_WIKI) }
    var linkText by rememberSaveable { mutableStateOf(if (fromWeb) "" else selection) }
    var url by rememberSaveable { mutableStateOf(if (fromWeb) selection.trim() else "") }
    var sectionId by rememberSaveable {
        mutableStateOf(tree.parentOfDocument(documentId)?.id ?: tree.root.id)
    }
    var pageId by rememberSaveable { mutableStateOf<String?>(null) }
    val fromPath = tree.pathOf(WikiItem.Document(tree.documentById(documentId) ?: return))

    fun insert(item: WikiItem, heading: Heading? = null) {
        val text = linkText.trim().ifEmpty { heading?.text ?: "" }
        onInsert(PickerMarkdown.markdownLink(tree, fromPath, item, text, heading?.id ?: "", asLink = true))
    }

    PickerScaffold(
        title = stringResource(R.string.link_picker_title),
        tabs = listOf(
            TAB_WIKI to stringResource(R.string.picker_tab_wiki),
            TAB_WEB to stringResource(R.string.picker_tab_web),
        ),
        selectedTab = tab,
        onTabSelected = { tab = it },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = linkText,
                onValueChange = { linkText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("link-text"),
                label = { Text(stringResource(R.string.link_picker_text)) },
                placeholder = {
                    Text(stringResource(if (tab == TAB_WIKI) R.string.link_picker_text_wiki_hint else R.string.link_picker_text_web_hint))
                },
                singleLine = true,
            )
            when {
                tab == TAB_WEB -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("link-url"),
                        label = { Text(stringResource(R.string.picker_address)) },
                        placeholder = { Text("https://") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            if (url.isNotBlank()) onInsert(PickerMarkdown.webLink(url, linkText))
                        }),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { onInsert(PickerMarkdown.webLink(url, linkText)) },
                            enabled = url.isNotBlank(),
                            modifier = Modifier.testTag("link-insert-web"),
                        ) {
                            Text(stringResource(R.string.picker_insert))
                        }
                    }
                }

                pageId != null -> {
                    val page = tree.documentById(pageId!!)
                    if (page == null) {
                        pageId = null
                    } else {
                        HeadingList(
                            page = page,
                            ownPage = page.id == documentId,
                            documentText = documentText,
                            textLoader = textLoader,
                            onBack = { pageId = null },
                            onPick = { heading -> insert(WikiItem.Document(page), heading) },
                        )
                    }
                }

                else -> WikiTreeBrowser(
                    tree = tree,
                    sectionId = sectionId,
                    onSectionChange = { sectionId = it },
                    onPick = { item ->
                        if (item is WikiItem.Document) pageId = item.id else insert(item)
                    },
                    modifier = Modifier.padding(top = 8.dp),
                    pickSections = true,
                    searchPlaceholder = stringResource(R.string.link_picker_search),
                    thumbnailLoader = thumbnailLoader,
                )
            }
        }
    }
}

@Composable
private fun HeadingList(
    page: DocumentData,
    ownPage: Boolean,
    documentText: String,
    textLoader: DocumentTextLoader,
    onBack: () -> Unit,
    onPick: (Heading?) -> Unit,
) {
    // null while loading
    var headings by remember(page.id) { mutableStateOf<List<Heading>?>(null) }
    var failed by remember(page.id) { mutableStateOf(false) }
    LaunchedEffect(page.id) {
        if (headings != null) return@LaunchedEffect
        val text = if (ownPage) documentText else textLoader.load(page.id)
        failed = text == null
        headings = text?.let { Headings.of(it) } ?: emptyList()
    }

    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("link-back")) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.picker_back))
            }
            Text(page.name, fontWeight = FontWeight.Bold)
        }
        LazyColumn {
            item {
                ListItem(
                    modifier = Modifier
                        .clickable { onPick(null) }
                        .testTag("link-page"),
                    headlineContent = {
                        Text(stringResource(if (ownPage) R.string.link_picker_this_page else R.string.link_picker_whole_page))
                    },
                    leadingContent = { Icon(Icons.Outlined.Description, contentDescription = null) },
                )
            }
            val list = headings
            when {
                list == null -> item {
                    Text(stringResource(R.string.link_picker_loading_headings), modifier = Modifier.padding(16.dp))
                }

                failed -> item {
                    Text(
                        stringResource(R.string.link_picker_headings_failed),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                list.isNotEmpty() -> {
                    item {
                        Text(
                            stringResource(R.string.link_picker_or_heading),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(list, key = { it.line }) { heading ->
                        ListItem(
                            modifier = Modifier
                                .clickable { onPick(heading) }
                                .padding(start = ((heading.level - 1) * 14).dp)
                                .testTag("link-heading"),
                            headlineContent = { Text(heading.text) },
                            supportingContent = { Text("#${heading.id}") },
                            leadingContent = { Icon(Icons.Outlined.Tag, contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

private const val TAB_WIKI = "wiki"
private const val TAB_WEB = "web"
