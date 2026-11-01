package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun RecentSearchList(
    modifier: Modifier = Modifier,
    terms: List<String>,
    onTermClicked: (String) -> Unit,
) {
    LazyColumn(modifier = modifier) {
        items(terms) { term ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTermClicked(term) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    modifier = Modifier.size(18.dp),
                    asset = MaterialDesignIconic.Icon.gmi_time_restore,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                )
                Text(
                    text = term,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        item {
            Spacer(modifier = Modifier.imePadding())
        }
    }
}

@CombinedPreview
@Composable
private fun RecentSearchListPreview() {
    MkDocsEditorTheme {
        RecentSearchList(
            terms = listOf("Haftpflicht", "Rezept Kartoffelsalat"),
            onTermClicked = {},
        )
    }
}
