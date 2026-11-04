package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry.DocumentListEntry
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry.ResourceListEntry
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry.SectionListEntry
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.util.Date

/**
 * @param content what is in the folder, null while that is unknown
 * @param isLoading whether the folder is loading: an unknown folder is not an empty one
 */
@Composable
internal fun FileBrowserList(
    content: FolderContent?,
    onDocumentClicked: (DocumentData) -> Unit,
    onDocumentLongClicked: (DocumentData) -> Unit,
    onResourceClicked: (ResourceData) -> Unit,
    onResourceLongClicked: (ResourceData) -> Unit,
    onSectionClicked: (SectionData) -> Unit,
    onSectionLongClicked: (SectionData) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    unsyncedDocumentIds: Set<String> = emptySet(),
    state: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        modifier = modifier,
        state = state,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        // the last entry can be scrolled out from under the button
        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 128.dp),
    ) {
        if (content == null || content.isEmpty) {
            if (content != null || !isLoading) {
                item(key = "empty") {
                    EmptyPathView(
                        modifier = Modifier
                            .defaultMinSize(minHeight = 128.dp)
                            .fillMaxWidth()
                    )
                }
            }
            return@LazyColumn
        }

        if (content.sections.isNotEmpty()) {
            item(key = "sections") { ListHeadline(R.string.file_browser_sections_headline, Modifier.animateItem()) }
            items(content.sections, key = { "section-${it.id}" }) { section ->
                Box(Modifier.animateItem()) {
                    SectionListEntry(item = section, onClick = onSectionClicked, onLongClick = onSectionLongClicked)
                }
            }
        }

        if (content.documents.isNotEmpty()) {
            item(key = "documents") { ListHeadline(R.string.file_browser_documents_headline, Modifier.animateItem()) }
            items(content.documents, key = { "document-${it.id}" }) { document ->
                Box(Modifier.animateItem()) {
                    DocumentListEntry(
                        item = document,
                        onClick = onDocumentClicked,
                        onLongClick = onDocumentLongClicked,
                        hasUnsyncedChanges = document.id in unsyncedDocumentIds,
                    )
                }
            }
        }

        if (content.resources.isNotEmpty()) {
            item(key = "resources") { ListHeadline(R.string.file_browser_resources_headline, Modifier.animateItem()) }
            items(content.resources, key = { "resource-${it.id}" }) { resource ->
                Box(Modifier.animateItem()) {
                    ResourceListEntry(item = resource, onClick = onResourceClicked, onLongClick = onResourceLongClicked)
                }
            }
        }
    }
}

@Composable
private fun ListHeadline(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(
        modifier = modifier.padding(start = 8.dp, top = 8.dp),
        text = stringResource(id = text),
        style = MaterialTheme.typography.headlineSmall.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
    )
}

@Composable
private fun EmptyPathView(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            modifier = Modifier
                .size(32.dp),
            asset = MaterialDesignIconic.Icon.gmi_folder,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
        )

        Text(
            modifier = Modifier.padding(16.dp),
            text = stringResource(id = R.string.file_browser_empty),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
        )
    }
}

@CombinedPreview
@Composable
private fun FileBrowserListEmptyPreview() {
    MkDocsEditorTheme {
        FileBrowserList(
            content = FolderContent(),
            onDocumentClicked = {},
            onDocumentLongClicked = {},
            onResourceClicked = {},
            onResourceLongClicked = {},
            onSectionClicked = {},
            onSectionLongClicked = {},
        )
    }
}

@CombinedPreview
@Composable
private fun FileBrowserListPreview() {
    MkDocsEditorTheme {
        FileBrowserList(
            content = FolderContent(
                sections = listOf(SectionData(
                    entityId = 1,
                    id = "1",
                    name = "Subsection",
                    subsections = listOf(),
                    documents = listOf(),
                    resources = listOf()
                )),
                documents = listOf(DocumentData(
                    entityId = 1,
                    id = "1",
                    name = "Sample Document",
                    filesize = 1234,
                    modtime = Date(),
                    url = "https://www.google.com",
                    content = null,
                    isOfflineAvailable = true
                )),
                resources = listOf(ResourceData(
                    entityId = 1,
                    id = "1",
                    name = "Sample Ressource.png",
                    filesize = 1234,
                    modtime = Date(),
                    isOfflineAvailable = true
                )),
            ),
            onDocumentClicked = {},
            onDocumentLongClicked = {},
            onResourceClicked = {},
            onResourceLongClicked = {},
            onSectionClicked = {},
            onSectionLongClicked = {},
        )
    }
}

