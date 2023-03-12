package de.markusressel.mkdocseditor.feature.browser.ui.compose

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview


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
        elevation = CardDefaults.cardElevation(4.dp)
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


@CombinedPreview
@Composable
private fun DocumentListEntryPreview() {
    MkDocsEditorTheme {
        DocumentListEntry(
            item = DocumentEntity(
                name = "Sample File.md"
            ),
            onClick = {}
        )
    }
}
