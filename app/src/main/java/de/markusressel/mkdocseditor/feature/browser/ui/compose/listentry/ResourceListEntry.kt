package de.markusressel.mkdocseditor.feature.browser.ui.compose.listentry

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.browser.data.ResourceData
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import java.util.Date


@Composable
internal fun ResourceListEntry(
    item: ResourceData,
    onClick: (ResourceData) -> Unit,
    onLongClick: (ResourceData) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(item) },
                onLongClick = { onLongClick(item) }
            )
            .then(modifier),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                modifier = Modifier
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_attachment,
                colorFilter = ColorFilter.tint(colorAttribute(R.attr.resourceBackgroundColor)),
            )

            Column {

                Text(
                    modifier = Modifier.padding(start = 8.dp),
                    text = item.name,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.titleMedium,
                )

                LastModifiedDate(
                    modifier = Modifier.padding(start = 8.dp),
                    modtime = item.modtime
                )
            }
        }
    }
}


@CombinedPreview
@Composable
private fun ResourceListEntryPreview() {
    MkDocsEditorTheme {
        ResourceListEntry(
            item = ResourceData(
                entityId = 1,
                id = "1",
                name = "Sample File.jpg",
                filesize = 1234,
                modtime = Date(),
            ),
            onClick = {},
            onLongClick = {}
        )
    }
}
