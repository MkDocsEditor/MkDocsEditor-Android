package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
fun ScreenTitle(
    modifier: Modifier = Modifier,
    title: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(color = MaterialTheme.colorScheme.primaryContainer)
            .then(modifier)
    ) {
        Text(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(8.dp),
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@CombinedPreview
@Composable
fun ScreenTitlePreview() {
    MkDocsEditorTheme {
        ScreenTitle(
            modifier = Modifier,
            title = "Screen Title"
        )
    }
}