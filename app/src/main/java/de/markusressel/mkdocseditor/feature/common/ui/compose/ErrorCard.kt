package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@CombinedPreview
@Composable
private fun ErrorCardPreview() {
    MkDocsEditorTheme {
        ErrorCard(
            text = "Something went wrong :(",
            onRetry = {}
        )
    }
}


@Composable
fun ErrorCard(
    modifier: Modifier = Modifier,
    text: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        onClick = onRetry,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier
                    .padding(8.dp),
                text = text,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Image(
                modifier = Modifier
                    .padding(8.dp)
                    .size(32.dp),
                asset = MaterialDesignIconic.Icon.gmi_refresh,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onErrorContainer),
            )
        }
    }
}