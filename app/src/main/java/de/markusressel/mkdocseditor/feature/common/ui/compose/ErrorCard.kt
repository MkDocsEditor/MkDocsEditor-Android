package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview
@Composable
private fun ErrorCardPreview() {
    ErrorCard(
        text = "Something went wrong :("
    )
}

@Composable
fun ErrorCard(modifier: Modifier = Modifier, text: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
    ) {
        Text(
            modifier = Modifier.padding(8.dp),
            text = text,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}