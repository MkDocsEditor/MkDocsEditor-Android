package de.markusressel.mkdocseditor.feature.filebrowser.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import kotlinx.coroutines.flow.collectLatest

/** the longest a folder name gets, so that a long one does not push away the others */
private val MAX_CHIP_WIDTH = 200.dp

/**
 * The way from the root down to the open folder. The root stays in place. The rest scrolls, and ends at the open folder
 * whenever the path changes, because that is the one the user wants to see.
 *
 * @param path the sections, starting with the root
 * @param onSectionClicked a parent was clicked; the open folder cannot be clicked
 */
@Composable
internal fun SectionPath(
    path: List<SectionItem>,
    onSectionClicked: (SectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(path) {
        // the new width is only known after the layout, and changes when the chips do
        snapshotFlow { scrollState.maxValue }.collectLatest { scrollState.animateScrollTo(it) }
    }

    Row(
        modifier = modifier
            .background(color = MaterialTheme.colorScheme.primaryContainer)
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

        Row(
            modifier = Modifier
                .weight(1F)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            path.drop(1).forEachIndexed { index, section ->
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

@Composable
private fun PathChip(
    section: SectionItem,
    isCurrent: Boolean,
    onClick: (SectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val content: @Composable () -> Unit = {
        Text(
            modifier = Modifier
                .widthIn(min = 24.dp, max = MAX_CHIP_WIDTH)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            text = section.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else null,
        )
    }

    if (isCurrent) {
        Surface(
            modifier = modifier.padding(2.dp),
            shape = shape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier,
            onClick = { onClick(section) },
            shape = shape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            content = content,
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
