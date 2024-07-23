package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

@Composable
internal fun CodeEditorBottomBar(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
    BottomAppBar(
        modifier = modifier,
        windowInsets = BottomAppBarDefaults.windowInsets.only(
            sides = WindowInsetsSides.Left + WindowInsetsSides.Right + WindowInsetsSides.Top
        )
    ) {
        IconButton(onClick = { onUiEvent(CodeEditorViewModel.UiEvent.FormatBoldClicked) }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_format_bold,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }

        IconButton(onClick = { onUiEvent(CodeEditorViewModel.UiEvent.FormatItalicClicked) }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_format_italic,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }

        IconButton(onClick = { onUiEvent(CodeEditorViewModel.UiEvent.FormatStrikethroughClicked) }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_format_strikethrough,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }

        IconButton(onClick = { onUiEvent(CodeEditorViewModel.UiEvent.FormatListBulletedClicked) }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_format_list_bulleted,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }

        IconButton(onClick = { onUiEvent(CodeEditorViewModel.UiEvent.FormatCodeBlockClicked) }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_code,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }

        IconButton(onClick = { onUiEvent(CodeEditorViewModel.UiEvent.InsertFileReferenceClicked) }) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_file_add,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        ExpandableFab(
            mainItemModifier = Modifier.size(48.dp),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(CodeEditorViewModel.UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
    }
}


@CombinedPreview
@Composable
private fun CodeEditorBottomBarPreview() {
    MkDocsEditorTheme {
        CodeEditorBottomBar(
            uiState = CodeEditorViewModel.UiState(),
            onUiEvent = {}
        )
    }
}