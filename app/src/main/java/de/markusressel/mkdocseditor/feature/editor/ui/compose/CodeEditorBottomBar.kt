package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.ExpandableFab
import de.markusressel.mkdocseditor.feature.editor.domain.AdmonitionTypes
import de.markusressel.mkdocseditor.feature.editor.domain.FormatAction
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * The formatting toolbar of the editor. It scrolls horizontally, so every action keeps a
 * full size touch target; headings and admonitions open a menu.
 */
@Composable
internal fun CodeEditorBottomBar(
    modifier: Modifier = Modifier,
    uiState: CodeEditorViewModel.UiState,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
    val format: (FormatAction) -> Unit = { onUiEvent(CodeEditorViewModel.UiEvent.FormatClicked(it)) }

    BottomAppBar(
        modifier = modifier,
        windowInsets = BottomAppBarDefaults.windowInsets.only(
            sides = WindowInsetsSides.Left + WindowInsetsSides.Right + WindowInsetsSides.Top
        )
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolbarButton(Icons.Default.FormatBold, R.string.format_bold) { format(FormatAction.Bold) }
            ToolbarButton(Icons.Default.FormatItalic, R.string.format_italic) { format(FormatAction.Italic) }
            ToolbarButton(Icons.Default.FormatStrikethrough, R.string.format_strikethrough) { format(FormatAction.Strikethrough) }
            ToolbarButton(Icons.Default.Code, R.string.format_inline_code) { format(FormatAction.InlineCode) }
            ToolbarButton(Icons.Default.DataObject, R.string.format_code_block) { format(FormatAction.CodeBlock) }
            HeadingButton(onSelected = { format(FormatAction.Heading(it)) })
            ToolbarButton(Icons.AutoMirrored.Default.FormatListBulleted, R.string.format_bullet_list) { format(FormatAction.BulletList) }
            ToolbarButton(Icons.Default.FormatListNumbered, R.string.format_numbered_list) { format(FormatAction.NumberedList) }
            ToolbarButton(Icons.Default.Checklist, R.string.format_task_list) { format(FormatAction.TaskList) }
            ToolbarButton(Icons.Default.FormatQuote, R.string.format_quote) { format(FormatAction.Quote) }
            ToolbarButton(Icons.Default.Link, R.string.format_link) { onUiEvent(CodeEditorViewModel.UiEvent.InsertLinkClicked) }
            ToolbarButton(Icons.Default.Image, R.string.format_image) { onUiEvent(CodeEditorViewModel.UiEvent.InsertImageClicked) }
            ToolbarButton(Icons.Default.AttachFile, R.string.format_resource) { onUiEvent(CodeEditorViewModel.UiEvent.InsertFileReferenceClicked) }
            ToolbarButton(Icons.Default.TableChart, R.string.format_table) { format(FormatAction.Table) }
            AdmonitionButton(onSelected = { format(FormatAction.Admonition(it)) })
            ToolbarButton(Icons.Default.HorizontalRule, R.string.format_horizontal_rule) { format(FormatAction.HorizontalRule) }
        }

        Spacer(modifier = Modifier.width(8.dp))

        ExpandableFab(
            mainItemModifier = Modifier.size(48.dp),
            items = uiState.fabConfig.right,
            onItemClicked = {
                onUiEvent(CodeEditorViewModel.UiEvent.ExpandableFabItemSelected(item = it))
            }
        )
    }
}

@Composable
private fun ToolbarButton(icon: ImageVector, description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = stringResource(description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HeadingButton(onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarButton(Icons.Default.Title, R.string.format_heading) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..6).forEach { level ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(R.string.format_heading_level, level),
                            style = when (level) {
                                1 -> MaterialTheme.typography.titleLarge
                                2 -> MaterialTheme.typography.titleMedium
                                else -> MaterialTheme.typography.titleSmall
                            },
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(level)
                    },
                )
            }
        }
    }
}

@Composable
private fun AdmonitionButton(onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ToolbarButton(Icons.Default.Info, R.string.format_admonition) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AdmonitionTypes.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        expanded = false
                        onSelected(type)
                    },
                )
            }
        }
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
