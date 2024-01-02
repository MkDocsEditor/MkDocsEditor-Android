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
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun FileBrowserList(
    items: List<Any>,
    onDocumentClicked: (DocumentEntity) -> Unit,
    onDocumentLongClicked: (DocumentEntity) -> Unit,
    onResourceClicked: (ResourceEntity) -> Unit,
    onResourceLongClicked: (ResourceEntity) -> Unit,
    onSectionClicked: (SectionEntity) -> Unit,
    onSectionLongClicked: (SectionEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(
                vertical = 8.dp,
                horizontal = 8.dp,
            )
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
    onDocumentClicked: (DocumentEntity) -> Unit,
    onDocumentLongClicked: (DocumentEntity) -> Unit,
    onResourceClicked: (ResourceEntity) -> Unit,
    onResourceLongClicked: (ResourceEntity) -> Unit,
    onSectionClicked: (SectionEntity) -> Unit,
    onSectionLongClicked: (SectionEntity) -> Unit,
) {
    when (item) {
        is DocumentEntity -> DocumentListEntry(
            item = item,
            onClick = onDocumentClicked,
            onLongClick = onDocumentLongClicked,
        )

        is ResourceEntity -> ResourceListEntry(
            item = item,
            onClick = onResourceClicked,
            onLongClick = onResourceLongClicked,
        )

        is SectionEntity -> SectionListEntry(
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
                SectionEntity(
                    name = "Subsection",
                ),
                DocumentEntity(
                    name = "Sample File.md"
                ).apply {
                    content.target = DocumentContentEntity(
                        text = "Text"
                    )
                },
                ResourceEntity(
                    name = "Sample Ressource.png"
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

