package de.markusressel.mkdocseditor.ui.compose

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig

@Preview
@Composable
fun ExpandableFabPreview() {
    ExpandableFab(
        modifier = Modifier.fillMaxSize(),
        items = listOf(
            FabConfig.Fab(
                id = 0,
                description = R.string.create_document,
                icon = MaterialDesignIconic.Icon.gmi_file_add,
            ),
            FabConfig.Fab(
                id = 1,
                description = R.string.create_section,
                icon = MaterialDesignIconic.Icon.gmi_folder,
            ),
        ),
        onItemClicked = {}
    )
}

@Composable
fun ExpandableFab(
    items: List<FabConfig.Fab>,
    onItemClicked: (FabConfig.Fab) -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.End,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .then(modifier),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = horizontalAlignment,
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier.padding(end = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                items.forEach {
                    Row(
                        modifier = Modifier.clickable { onItemClicked(it) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Card(
                            modifier = Modifier.padding(
                                top = 4.dp,
                                bottom = 4.dp,
                                start = 8.dp,
                                end = 16.dp,
                            )
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    modifier = Modifier.padding(8.dp),
                                    textAlign = TextAlign.Center,
                                    text = stringResource(id = it.description)
                                )
                            }
                        }

                        FloatingActionButton(
                            modifier = Modifier.size(38.dp),
                            onClick = {
                                onItemClicked(it)
                            }
                        ) {
                            Image(
                                modifier = Modifier.size(16.dp),
                                asset = it.icon,
                                colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        // main button to toggle options
        FloatingActionButton(
            onClick = {
                expanded = expanded.not()
            }
        ) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_plus,
                colorFilter = ColorFilter.tint(MaterialTheme.colors.primary),
            )
        }
    }
}
