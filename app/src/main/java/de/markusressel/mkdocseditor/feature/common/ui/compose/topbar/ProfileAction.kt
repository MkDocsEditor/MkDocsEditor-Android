package de.markusressel.mkdocseditor.feature.common.ui.compose.topbar

import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun ProfileAction(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(
        modifier = modifier,
        onClick = onClick
    ) {
        Image(
            asset = MaterialDesignIconic.Icon.gmi_account,
            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
        )
    }
}

@CombinedPreview
@Composable
private fun SearchActionPreview() {
    MkDocsEditorTheme {
        ProfileAction(
            modifier = Modifier,
            onClick = {}
        )
    }
}