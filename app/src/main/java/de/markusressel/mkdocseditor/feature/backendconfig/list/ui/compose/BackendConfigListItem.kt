package de.markusressel.mkdocseditor.feature.backendconfig.list.ui.compose

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig

@Composable
internal fun BackendConfigListItem(
    item: BackendConfig,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier
            .clip(CardDefaults.elevatedShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .then(modifier),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 6.dp
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = item.isSelected, onClick = onClick
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
            ) {
                Text(
                    text = item.name, style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = item.description, style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = item.serverConfig?.domain ?: "", style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
