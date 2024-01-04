package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import de.markusressel.mkdocseditor.feature.browser.data.DocumentData
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.browser.data.SectionData
import de.markusressel.mkdocseditor.feature.browser.ui.compose.listentry.DocumentListEntry
import de.markusressel.mkdocseditor.feature.browser.ui.compose.listentry.ResourceListEntry
import de.markusressel.mkdocseditor.feature.browser.ui.compose.listentry.SectionListEntry
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.util.Date

@Composable
internal fun FileBrowserList(
    items: List<Any>,
    onDocumentClicked: (DocumentData) -> Unit,
    onDocumentLongClicked: (DocumentData) -> Unit,
    onResourceClicked: (ResourceData) -> Unit,
    onResourceLongClicked: (ResourceData) -> Unit,
    onSectionClicked: (SectionData) -> Unit,
    onSectionLongClicked: (SectionData) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(
                vertical = 8.dp,
                horizontal = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {

        if (items.isEmpty()) {
            EmptyPathView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        } else {
            items.forEachIndexed { _, item ->
                FileBrowserListEntry(
                    item = item,
                    onDocumentClicked = onDocumentClicked,
                    onDocumentLongClicked = onDocumentLongClicked,
                    onResourceClicked = onResourceClicked,
                    onResourceLongClicked = onResourceLongClicked,
                    onSectionClicked = onSectionClicked,
                    onSectionLongClicked = onSectionLongClicked,
                )
            }

            Spacer(modifier = Modifier.height(128.dp))
        }
    }
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

@Composable
internal fun FileBrowserListEntry(
    item: Any,
    onDocumentClicked: (DocumentData) -> Unit,
    onDocumentLongClicked: (DocumentData) -> Unit,
    onResourceClicked: (ResourceData) -> Unit,
    onResourceLongClicked: (ResourceData) -> Unit,
    onSectionClicked: (SectionData) -> Unit,
    onSectionLongClicked: (SectionData) -> Unit,
) {
    when (item) {
        is DocumentData -> DocumentListEntry(
            item = item,
            onClick = onDocumentClicked,
            onLongClick = onDocumentLongClicked,
        )

        is ResourceData -> ResourceListEntry(
            item = item,
            onClick = onResourceClicked,
            onLongClick = onResourceLongClicked,
        )

        is SectionData -> SectionListEntry(
            item = item,
            onClick = onSectionClicked,
            onLongClick = onSectionLongClicked
        )
    }
}

@CombinedPreview
@Composable
private fun FileBrowserListEmptyPreview() {
    MkDocsEditorTheme {
        FileBrowserList(
            items = listOf(),
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
            items = listOf(
                SectionData(
                    entityId = 1,
                    id = "1",
                    name = "Subsection",
                    subsections = listOf(),
                    documents = listOf(),
                    resources = listOf()
                ),
                DocumentData(
                    entityId = 1,
                    id = "1",
                    name = "Sample Document",
                    filesize = 1234,
                    modtime = Date(),
                    url = "https://www.google.com",
                    content = null,
                    isOfflineAvailable = true
                ),
                ResourceData(
                    entityId = 1,
                    id = "1",
                    name = "Sample Ressource.png",
                    filesize = 1234,
                    modtime = Date(),
                )
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

