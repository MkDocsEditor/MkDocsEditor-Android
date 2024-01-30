package de.markusressel.mkdocseditor.feature.search.ui.compose

import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.markusressel.mkdocseditor.feature.search.domain.SearchResultItem

@Composable
internal fun <T : SearchResultItem> SearchResultCard(
    modifier: Modifier = Modifier,
    item: T,
    onItemClicked: (T) -> Unit,
    onItemLongClicked: (T) -> Unit,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .combinedClickable(
                onClick = { onItemClicked(item) },
                onLongClick = { onItemLongClicked(item) }
            )
            .then(modifier),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        content()
    }
}