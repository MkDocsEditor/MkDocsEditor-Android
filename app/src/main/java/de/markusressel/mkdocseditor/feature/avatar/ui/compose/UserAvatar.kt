package de.markusressel.mkdocseditor.feature.avatar.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
import de.markusressel.mkdocseditor.feature.editor.ui.compose.labelTextColorOn
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * The profile picture of a user in a circle. Without one, and while it loads, the circle shows the
 * initial of [name] on [color].
 *
 * @param avatar the picture, null for none
 */
@Composable
internal fun UserAvatar(
    name: String,
    color: Color,
    avatar: Avatar?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initialOf(name),
            color = labelTextColorOn(color),
            style = textStyle,
        )
        if (avatar != null) {
            AsyncImage(
                model = avatar,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** the first character, an emoji or a letter outside the BMP stays whole */
internal fun initialOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return ""
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}

@CombinedPreview
@Composable
private fun UserAvatarPreview() {
    MkDocsEditorTheme {
        UserAvatar(
            modifier = Modifier.size(40.dp),
            name = "Anna",
            color = Color(0xFF1971C2),
            avatar = null,
        )
    }
}
