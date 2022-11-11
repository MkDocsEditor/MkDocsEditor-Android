package de.markusressel.mkdocseditor.feature.browser.ui.compose

import android.view.View
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.data.persistence.IdentifiableListItem
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity

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
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
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

@OptIn(ExperimentalMaterialApi::class)
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
        elevation = 4.dp,
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
                    colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
                )

                if (item.offlineAvailableVisibility == View.VISIBLE) {
                    Image(
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.BottomEnd),
                        asset = MaterialDesignIconic.Icon.gmi_save,
                        colorFilter = ColorFilter.tint(Color.Companion.White),
                    )
                }
            }

            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = item.name
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
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
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_attachment,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
            )

            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = item.name
            )
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
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
        elevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_folder,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
            )

            Text(
                modifier = Modifier.padding(start = 8.dp),
                text = item.name
            )
        }
    }
}