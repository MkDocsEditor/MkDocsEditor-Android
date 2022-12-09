package de.markusressel.mkdocseditor.feature.browser.ui.compose

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity

@Preview
@Composable
private fun FileBrowserListEmptyPreview() {
    FileBrowserList(
        items = listOf(),
        onDocumentClicked = {},
        onResourceClicked = {},
        onSectionClicked = {},
    )
}

@Preview
@Composable
private fun FileBrowserListPreview() {
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
        onResourceClicked = {},
        onSectionClicked = {},
    )
}

@Composable
internal fun FileBrowserList(
    items: List<IdentifiableListItem>,
    onDocumentClicked: (DocumentEntity) -> Unit,
    onResourceClicked: (ResourceEntity) -> Unit,
    onSectionClicked: (SectionEntity) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            //.verticalScroll(rememberScrollState())
            .padding(
                vertical = 8.dp,
                horizontal = 8.dp,
            )
    ) {

        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
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
        } else {
            items.forEachIndexed { index, item ->
                FileBrowserListEntry(
                    item = item,
                    onDocumentClicked = onDocumentClicked,
                    onResourceClicked = onResourceClicked,
                    onSectionClicked = onSectionClicked,
                )

                if (index < items.size) {
                    Divider()
                }
            }

            Spacer(modifier = Modifier.height(128.dp))
        }
    }
}

@Composable
internal fun FileBrowserListEntry(
    item: IdentifiableListItem,
    onDocumentClicked: (DocumentEntity) -> Unit,
    onResourceClicked: (ResourceEntity) -> Unit,
    onSectionClicked: (SectionEntity) -> Unit,
) {
    when (item) {
        is DocumentEntity -> DocumentListEntry(
            item = item,
            onClick = onDocumentClicked
        )
        is ResourceEntity -> ResourceListEntry(
            item = item,
            onClick = onResourceClicked
        )
        is SectionEntity -> SectionListEntry(
            item = item,
            onClick = onSectionClicked
        )
    }
}

@Preview
@Composable
private fun DocumentListEntryPreview() {
    DocumentListEntry(
        item = DocumentEntity(
            name = "Sample File.md"
        ),
        onClick = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DocumentListEntry(
    item: DocumentEntity,
    onClick: (DocumentEntity) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
        onClick = { onClick(item) },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Image(
                    modifier = Modifier
                        .size(32.dp),
                    asset = MaterialDesignIconic.Icon.gmi_file,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                )

                if (item.offlineAvailableVisibility == View.VISIBLE) {
                    Image(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd),
                        asset = MaterialDesignIconic.Icon.gmi_save,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimary),
                    )
                }
            }

            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = item.name,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResourceListEntry(
    item: ResourceEntity,
    onClick: (ResourceEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
        onClick = { onClick(item) },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_attachment,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )

            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = item.name,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionListEntry(
    item: SectionEntity,
    onClick: (SectionEntity) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 8.dp,
                vertical = 4.dp
            ),
        onClick = { onClick(item) },
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_folder,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
            )

            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = item.name,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}