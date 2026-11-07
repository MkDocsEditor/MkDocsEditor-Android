package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.common.ui.compose.horizontalBarsAndCutoutPadding
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/** the longest a folder name gets, so that a long one does not push away the others */
private val MAX_CHIP_WIDTH = 200.dp

/**
 * The chip of the open folder while the back gesture takes it away
 *
 * @param progress how far, between 0 (shown) and 1 (gone)
 */
internal data class LeavingChip(val sectionId: String, val progress: Float)

/** @return how visible the chip of [sectionId] is while [leaving] goes */
internal fun chipAlpha(sectionId: String, leaving: LeavingChip?): Float =
    if (leaving?.sectionId == sectionId) 1F - leaving.progress.coerceIn(0F, 1F) else 1F

/**
 * The way from the root down to the open folder. The root stays in place. The rest scrolls, and ends at the open folder
 * whenever the path changes, because that is the one the user wants to see.
 *
 * @param path the sections, starting with the root
 * @param onSectionClicked a parent was clicked; the open folder cannot be clicked
 * @param leavingChip the chip that fades with the back gesture. It is read while drawing, so the gesture only redraws.
 */
@Composable
internal fun SectionPath(
    path: List<SectionItem>,
    onSectionClicked: (SectionItem) -> Unit,
    modifier: Modifier = Modifier,
    leavingChip: () -> LeavingChip? = { null },
) {
    val listState = rememberLazyListState()
    LaunchedEffect(path) {
        // the list shows the new path from the next frame on
        withFrameNanos {}
        listState.scrollToEnd()
    }

    Row(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.primaryContainer)
            // in landscape the navigation bar or the cutout can be at either end
            .horizontalBarsAndCutoutPadding()
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val root = path.firstOrNull()
        if (root != null) {
            val rootDescription = stringResource(R.string.section_path_root)
            PathChip(
                modifier = Modifier.semantics { contentDescription = rootDescription },
                section = root,
                isCurrent = path.size == 1,
                onClick = onSectionClicked,
            )
        }

        LazyRow(
            modifier = Modifier.weight(1F),
            state = listState,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(path.drop(1), key = { _, section -> section.id }) { index, section ->
                Row(
                    modifier = Modifier
                        .animateItem(
                            fadeInSpec = tween(FOLDER_ANIMATION_MS),
                            placementSpec = tween(FOLDER_ANIMATION_MS),
                            fadeOutSpec = tween(FOLDER_ANIMATION_MS),
                        )
                        .graphicsLayer { alpha = chipAlpha(section.id, leavingChip()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        modifier = Modifier.size(16.dp),
                        asset = MaterialDesignIconic.Icon.gmi_chevron_right,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onPrimaryContainer),
                    )
                    PathChip(
                        section = section,
                        isCurrent = index == path.lastIndex - 1,
                        onClick = onSectionClicked,
                    )
                }
            }
        }
    }
}

/**
 * Scrolls until the last item ends at the end of the viewport. Not with [LazyListState.animateScrollToItem]: while that
 * runs, the list skips its item animations, so a chip that was removed in the meantime vanishes instead of fading out.
 */
private suspend fun LazyListState.scrollToEnd() {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0 || !canScrollForward) return
    // an item is only measured once it reaches into the viewport, and a new one starts right behind its end
    if (layoutInfo.visibleItemsInfo.none { it.index == last }) scrollBy(1F)
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == last }
    if (item == null) {
        // several new folders at once, too far for an animation
        scrollToItem(last)
        return
    }
    animateScrollBy(
        value = (item.offset + item.size - layoutInfo.viewportEndOffset).toFloat(),
        animationSpec = tween(FOLDER_ANIMATION_MS),
    )
}

@Composable
private fun PathChip(
    section: SectionItem,
    isCurrent: Boolean,
    onClick: (SectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val colors = MaterialTheme.colorScheme
    val color by animateColorAsState(
        targetValue = if (isCurrent) colors.primary else colors.secondaryContainer,
        animationSpec = tween(FOLDER_ANIMATION_MS),
        label = "chip color",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isCurrent) colors.onPrimary else colors.onSecondaryContainer,
        animationSpec = tween(FOLDER_ANIMATION_MS),
        label = "chip content color",
    )
    // one layout for both states: the size and the font stay the same, so nothing moves when a chip changes its state
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(shape)
            .background(color)
            .then(if (isCurrent) Modifier else Modifier.clickable(role = Role.Button) { onClick(section) }),
    ) {
        Text(
            modifier = Modifier
                .widthIn(min = 24.dp, max = MAX_CHIP_WIDTH)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            text = section.name,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@CombinedPreview
@Composable
private fun SectionPathPreview() {
    MkDocsEditorTheme {
        SectionPath(
            modifier = Modifier.fillMaxWidth(),
            path = listOf(
                SectionItem("0", "/"),
                SectionItem("1", "test"),
                SectionItem("2", "folder"),
                SectionItem("3", "a"),
                SectionItem("4", "b"),
                SectionItem("5", "c"),
            ),
            onSectionClicked = {}
        )
    }
}
