package de.markusressel.mkdocseditor.feature.auth.account.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.godaddy.android.colorpicker.ClassicColorPicker
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.ui.compose.parsePeerColor
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

/**
 * The color of the profile: typed as hex, or picked with the swatch at its end
 *
 * @param value the text of the field, like `#3f51b5`
 * @param fallback what the swatch shows while [value] is no color
 */
@Composable
internal fun ProfileColorField(
    value: String,
    fallback: Color,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    // the color the picker starts from. Only typing changes it, the picker itself keeps its
    // state while dragging, and a restart on each of its changes would jump the handles.
    var pickerStart by rememberSaveable { mutableStateOf(value) }
    val color = parsePeerColor(value.trim()) ?: fallback

    Column(modifier = modifier) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = value,
            onValueChange = {
                onValueChange(it)
                if (parsePeerColor(it.trim()) != null) pickerStart = it
            },
            singleLine = true,
            label = { Text(stringResource(R.string.account_color)) },
            supportingText = { Text(stringResource(R.string.account_color_hint)) },
            trailingIcon = {
                IconButton(onClick = { pickerOpen = !pickerOpen }) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(color, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    )
                }
            },
        )
        AnimatedVisibility(visible = pickerOpen) {
            key(pickerStart) {
                ClassicColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(vertical = 8.dp),
                    color = parsePeerColor(pickerStart.trim()) ?: fallback,
                    // the server takes colors without alpha
                    showAlphaBar = false,
                    onColorChanged = { onValueChange(it.toColor().toHex()) },
                )
            }
        }
    }
}

/** like `#3f51b5`, the format of the server */
internal fun Color.toHex(): String = "#%06x".format(toArgb() and 0xFFFFFF)

@CombinedPreview
@Composable
private fun ProfileColorFieldPreview() {
    MkDocsEditorTheme {
        ProfileColorField(
            value = "#3f51b5",
            fallback = Color.Gray,
            onValueChange = {},
        )
    }
}
