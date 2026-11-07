package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.ui.CodeEditorViewModel
import de.markusressel.mkdocseditor.feature.preferences.data.EDITOR_TEXT_SIZE_RANGE
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import kotlin.math.roundToInt

internal const val VIEW_SHEET_SCROLL_SYNC_TAG = "view-sheet-scroll-sync"
internal const val VIEW_SHEET_LINE_WRAPPING_TAG = "view-sheet-line-wrapping"
internal const val VIEW_SHEET_LINE_NUMBERS_TAG = "view-sheet-line-numbers"
internal const val VIEW_SHEET_TEXT_SIZE_TAG = "view-sheet-text-size"
internal const val VIEW_SHEET_ACCOUNT_COLOR_TAG = "view-sheet-account-color"

/**
 * The view settings of the editor, as a shortcut to the same settings of the Settings tab. Each change applies at once,
 * and the sheet leaves the editor visible above it, so the effect can be seen while choosing.
 *
 * @param settings the settings as they are stored
 * @param localColor the color of the own cursor as `#rrggbb`, null for the color of the account
 * @param splitOpen whether editor and rendered page are both shown, only then the scroll sync matters
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorViewSheet(
    settings: EditorSettings,
    localColor: String?,
    splitOpen: Boolean,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onUiEvent(CodeEditorViewModel.UiEvent.DismissDialog) },
        sheetState = rememberModalBottomSheetState(),
        // no dimming, the editor behind shows what each change does
        scrimColor = Color.Transparent,
    ) {
        EditorViewSheetContent(
            settings = settings,
            localColor = localColor,
            splitOpen = splitOpen,
            onUiEvent = onUiEvent,
        )
    }
}

@Composable
internal fun EditorViewSheetContent(
    settings: EditorSettings,
    localColor: String?,
    splitOpen: Boolean,
    onUiEvent: (CodeEditorViewModel.UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            text = stringResource(R.string.editor_view_settings),
            style = MaterialTheme.typography.titleLarge,
        )

        SectionTitle(stringResource(R.string.editor_author_highlight_title))
        AuthorHighlightButtons(
            mode = settings.authorHighlight,
            onModeChosen = { onUiEvent(CodeEditorViewModel.UiEvent.AuthorHighlightChosen(it)) },
        )

        if (splitOpen) {
            SwitchRow(
                tag = VIEW_SHEET_SCROLL_SYNC_TAG,
                text = stringResource(R.string.editor_scroll_sync_title),
                checked = settings.scrollSync,
                onCheckedChange = { onUiEvent(CodeEditorViewModel.UiEvent.ScrollSyncChanged(it)) },
            )
        }
        SwitchRow(
            tag = VIEW_SHEET_LINE_WRAPPING_TAG,
            text = stringResource(R.string.editor_line_wrapping_title),
            checked = settings.lineWrapping,
            onCheckedChange = { onUiEvent(CodeEditorViewModel.UiEvent.LineWrappingChanged(it)) },
        )
        SwitchRow(
            tag = VIEW_SHEET_LINE_NUMBERS_TAG,
            text = stringResource(R.string.editor_line_numbers_title),
            checked = settings.lineNumbers,
            onCheckedChange = { onUiEvent(CodeEditorViewModel.UiEvent.LineNumbersChanged(it)) },
        )

        TextSizeSlider(
            size = settings.textSize,
            onSizeChanged = { onUiEvent(CodeEditorViewModel.UiEvent.TextSizeChanged(it)) },
        )

        SectionTitle(stringResource(R.string.cursor_color))
        Text(
            modifier = Modifier.padding(horizontal = 24.dp),
            text = stringResource(R.string.cursor_color_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CursorColorSwatches(
            current = localColor,
            onColorChosen = { onUiEvent(CodeEditorViewModel.UiEvent.CursorColorChosen(it)) },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        text = text,
        style = MaterialTheme.typography.titleSmall,
    )
}

@Composable
private fun AuthorHighlightButtons(
    mode: AuthorHighlightMode,
    onModeChosen: (AuthorHighlightMode) -> Unit,
) {
    // in the order of their strength
    val modes = listOf(AuthorHighlightMode.Off, AuthorHighlightMode.Strip, AuthorHighlightMode.StripAndMarker)
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            // all as high as one with two lines
            .height(IntrinsicSize.Min),
    ) {
        modes.forEachIndexed { index, option ->
            SegmentedButton(
                modifier = Modifier.fillMaxHeight(),
                selected = option == mode,
                onClick = { onModeChosen(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                // without the check mark, so that the longest name has room; the color shows the choice
                icon = {},
                // on a narrow phone the longest name takes two lines rather than being cut off
                label = { Text(stringResource(option.shortNameRes), maxLines = 2, textAlign = TextAlign.Center) },
            )
        }
    }
}

/** shorter than the names in the settings, so that three fit next to each other on a phone */
private val AuthorHighlightMode.shortNameRes: Int
    get() = when (this) {
        AuthorHighlightMode.Off -> R.string.editor_author_highlight_off_name
        AuthorHighlightMode.Strip -> R.string.editor_view_author_strip
        AuthorHighlightMode.StripAndMarker -> R.string.editor_view_author_strip_and_marker
    }

@Composable
private fun SwitchRow(
    tag: String,
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(modifier = Modifier.weight(1f), text = text)
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** the same range and steps as the setting */
@Composable
private fun TextSizeSlider(
    size: Int,
    onSizeChanged: (Int) -> Unit,
) {
    // while dragging the slider follows the finger, each new size is written at once
    var dragged by remember { mutableStateOf<Float?>(null) }
    val shown = dragged?.roundToInt() ?: size
    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Text(modifier = Modifier.weight(1f), text = stringResource(R.string.editor_text_size_title))
            Text(text = stringResource(R.string.editor_view_text_size_value, shown))
        }
        Slider(
            modifier = Modifier.testTag(VIEW_SHEET_TEXT_SIZE_TAG),
            value = dragged ?: size.toFloat(),
            onValueChange = { value ->
                dragged = value
                val newSize = value.roundToInt()
                if (newSize != size) onSizeChanged(newSize)
            },
            onValueChangeFinished = { dragged = null },
            valueRange = EDITOR_TEXT_SIZE_RANGE.first.toFloat()..EDITOR_TEXT_SIZE_RANGE.last.toFloat(),
            steps = textSizeSliderSteps(EDITOR_TEXT_SIZE_RANGE),
        )
    }
}

/** the stops between the ends of the slider, one for each whole size */
internal fun textSizeSliderSteps(range: IntRange): Int = (range.last - range.first - 1).coerceAtLeast(0)

/** the colors of the own cursor to choose from, the same as in the view panel of the web client */
internal val cursorColorSwatches = listOf(
    "#1971c2", "#0c8599", "#2f9e44", "#66a80f", "#f08c00", "#e8590c",
    "#e03131", "#c2255c", "#9c36b5", "#6741d9", "#3b5bdb", "#868e96",
)

/** the color of the account first, then the colors to choose from */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CursorColorSwatches(
    current: String?,
    onColorChosen: (String?) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val accountLabel = stringResource(R.string.cursor_color_reset)
        Swatch(
            modifier = Modifier
                .testTag(VIEW_SHEET_ACCOUNT_COLOR_TAG)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            selected = current == null,
            outline = outline,
            label = accountLabel,
            onClick = { onColorChosen(null) },
        ) {
            Image(
                modifier = Modifier.size(24.dp),
                asset = MaterialDesignIconic.Icon.gmi_account,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
        cursorColorSwatches.forEach { value ->
            val color = parsePeerColor(value) ?: return@forEach
            val selected = value.equals(current, ignoreCase = true)
            Swatch(
                modifier = Modifier.background(color, CircleShape),
                selected = selected,
                outline = outline,
                label = value,
                onClick = { onColorChosen(value) },
            ) {
                if (selected) {
                    Image(
                        modifier = Modifier.size(24.dp),
                        asset = MaterialDesignIconic.Icon.gmi_check,
                        // whichever stands out on the swatch
                        colorFilter = ColorFilter.tint(if (color.luminance() > 0.4f) Color.Black else Color.White),
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(
    modifier: Modifier,
    selected: Boolean,
    outline: Color,
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .then(modifier)
            .then(if (selected) Modifier.border(3.dp, outline, CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@CombinedPreview
@Composable
private fun EditorViewSheetContentPreview() {
    MkDocsEditorTheme {
        EditorViewSheetContent(
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow),
            settings = EditorSettings(authorHighlight = AuthorHighlightMode.Strip),
            localColor = "#9c36b5",
            splitOpen = true,
            onUiEvent = {},
        )
    }
}
