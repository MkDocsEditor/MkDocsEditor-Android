package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import kotlin.math.roundToInt

/** How the edit mode divides its space between the code editor and the rendered page */
enum class SplitMode {
    /** only the code editor */
    Off,

    /** the editor above the page */
    Vertical,

    /** the editor next to the page */
    Horizontal;

    /** the mode the toggle goes to */
    fun next(): SplitMode = when (this) {
        Off -> Vertical
        Vertical -> Horizontal
        Horizontal -> Off
    }
}

internal const val DEFAULT_SPLIT_RATIO = 0.5f
internal const val MIN_SPLIT_RATIO = 0.15f
internal const val MAX_SPLIT_RATIO = 0.85f

/** at least this wide for two panes, so a foldable splits and a phone does not */
private const val WIDE_SCREEN_WIDTH_DP = 600

/**
 * @return the mode of a window until the user chooses one. A phone in landscape is wide enough, but has no height to
 * spare for the keyboard.
 */
internal fun defaultSplitMode(widthDp: Int, heightDp: Int): SplitMode = when {
    widthDp >= WIDE_SCREEN_WIDTH_DP && heightDp >= SHORT_SCREEN_HEIGHT_DP -> SplitMode.Horizontal
    else -> SplitMode.Off
}

internal const val SPLIT_DIVIDER_TAG = "split-divider"

/** the bar, the panes and the icon of a mode change move together */
internal const val EDITOR_MODE_ANIMATION_MS = 250

private val DIVIDER_TOUCH_SIZE = 24.dp

/**
 * The code editor and the rendered page. Both stay in the same place of the composition whatever the mode is: a new
 * editor would lose the focus and close the keyboard. The page comes up the first time it is shown, because a WebView
 * takes long to create and an edit mode that opens on its own may never need it. From then on it stays even without any
 * room, because a new one would be empty until it has loaded, which is longer than the animation that brings it back.
 *
 * @param modifier compose modifiers
 * @param showEditor whether the code editor is shown
 * @param showPreview whether the rendered page is shown
 * @param splitMode how the two are arranged when both are shown
 * @param ratio the share of the code editor, when both are shown
 * @param onRatioChange the user moves the divider to this ratio
 * @param onRatioChangeFinished the user let go of the divider
 * @param editor the code editor
 * @param preview the rendered page
 */
@Composable
internal fun EditorPanes(
    modifier: Modifier = Modifier,
    showEditor: Boolean,
    showPreview: Boolean,
    splitMode: SplitMode,
    ratio: Float,
    onRatioChange: (Float) -> Unit,
    onRatioChangeFinished: () -> Unit,
    editor: @Composable () -> Unit,
    preview: @Composable () -> Unit,
) {
    val arrangement = Triple(showEditor, showPreview, splitMode)
    // the share of the editor, without a split all or nothing of the space
    val targetShare = when {
        !showEditor -> 0f
        !showPreview -> 1f
        else -> ratio
    }
    val animatedShare = remember { Animatable(targetShare) }
    var settledArrangement by remember { mutableStateOf(arrangement) }
    val animating = arrangement != settledArrangement
    LaunchedEffect(arrangement, targetShare) {
        if (arrangement != settledArrangement) {
            animatedShare.animateTo(targetShare, tween(EDITOR_MODE_ANIMATION_MS))
            settledArrangement = arrangement
        } else {
            animatedShare.snapTo(targetShare)
        }
    }
    val currentTargetShare by rememberUpdatedState(targetShare)
    // read while measuring, so a moving divider does not compose the panes again
    val share = { if (settledArrangement == arrangement) currentTargetShare else animatedShare.value }

    val editorComposed = showEditor || animating
    var previewComposed by remember { mutableStateOf(showPreview) }
    if (showPreview) previewComposed = true
    val split = editorComposed && (showPreview || animating)
    // stays the same while the panes move, also when the split goes away
    var lastSideBySide by remember { mutableStateOf(splitMode == SplitMode.Horizontal) }
    if (splitMode != SplitMode.Off) lastSideBySide = splitMode == SplitMode.Horizontal
    val sideBySide = lastSideBySide
    // only the gesture reads it, so the layout does not depend on it
    val lengthAlongDivider = remember { floatArrayOf(1f) }

    Layout(
        modifier = modifier,
        content = {
            Box { if (editorComposed) editor() }
            Box { if (previewComposed) preview() }
            if (split) {
                SplitDivider(
                    sideBySide = sideBySide,
                    ratio = ratio,
                    length = { lengthAlongDivider[0] },
                    onRatioChange = onRatioChange,
                    onRatioChangeFinished = onRatioChangeFinished,
                )
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val length = if (sideBySide) width else height
        lengthAlongDivider[0] = length.coerceAtLeast(1).toFloat()
        val editorLength = (length * share()).roundToInt()

        fun fixed(paneLength: Int) =
            if (sideBySide) Constraints.fixed(paneLength, height) else Constraints.fixed(width, paneLength)

        val editorPane = measurables[0].measure(fixed(editorLength))
        val previewPane = measurables[1].measure(fixed(length - editorLength))
        val touchSize = DIVIDER_TOUCH_SIZE.roundToPx()
        val divider = measurables.getOrNull(2)?.measure(
            if (sideBySide) Constraints.fixed(touchSize, height) else Constraints.fixed(width, touchSize)
        )

        layout(width, height) {
            editorPane.place(0, 0)
            previewPane.place(
                x = if (sideBySide) editorLength else 0,
                y = if (sideBySide) 0 else editorLength,
            )
            // over the edges of both panes, so it is easy to grab without taking room from them
            divider?.place(
                x = if (sideBySide) editorLength - touchSize / 2 else 0,
                y = if (sideBySide) 0 else editorLength - touchSize / 2,
            )
        }
    }
}

@Composable
private fun SplitDivider(
    sideBySide: Boolean,
    ratio: Float,
    length: () -> Float,
    onRatioChange: (Float) -> Unit,
    onRatioChangeFinished: () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.split_divider)
    val currentRatio by rememberUpdatedState(ratio)
    val currentOnRatioChange by rememberUpdatedState(onRatioChange)
    val currentOnFinished by rememberUpdatedState(onRatioChangeFinished)

    Box(
        modifier = Modifier
            .testTag(SPLIT_DIVIDER_TAG)
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(ratio, MIN_SPLIT_RATIO..MAX_SPLIT_RATIO)
                setProgress { value ->
                    onRatioChange(value.coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO))
                    onRatioChangeFinished()
                    true
                }
            }
            .pointerInput(sideBySide) {
                // the state only arrives with the next frame, so the gesture adds up on its own
                var dragged = currentRatio
                detectDragGestures(
                    onDragStart = { dragged = currentRatio },
                    onDragEnd = { currentOnFinished() },
                    onDragCancel = { currentOnFinished() },
                ) { change, amount ->
                    change.consume()
                    val along = if (sideBySide) amount.x else amount.y
                    dragged = (dragged + along / length()).coerceIn(MIN_SPLIT_RATIO, MAX_SPLIT_RATIO)
                    currentOnRatioChange(dragged)
                }
            }
            .drawBehind {
                val line = 1.dp.toPx()
                val handleLong = 36.dp.toPx()
                val handleThick = 4.dp.toPx()
                if (sideBySide) {
                    drawRect(lineColor, Offset((size.width - line) / 2, 0f), Size(line, size.height))
                    drawRoundRect(
                        color = handleColor,
                        topLeft = Offset((size.width - handleThick) / 2, (size.height - handleLong) / 2),
                        size = Size(handleThick, handleLong),
                        cornerRadius = CornerRadius(handleThick / 2),
                    )
                } else {
                    drawRect(lineColor, Offset(0f, (size.height - line) / 2), Size(size.width, line))
                    drawRoundRect(
                        color = handleColor,
                        topLeft = Offset((size.width - handleLong) / 2, (size.height - handleThick) / 2),
                        size = Size(handleLong, handleThick),
                        cornerRadius = CornerRadius(handleThick / 2),
                    )
                }
            },
    )
}
