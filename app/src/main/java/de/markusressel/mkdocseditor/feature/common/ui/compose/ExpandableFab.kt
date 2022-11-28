package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Column(
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    modifier = Modifier.padding(8.dp),
                                    textAlign = TextAlign.Center,
                                    text = stringResource(id = it.description),
                                    color = MaterialTheme.colorScheme.onTertiary,
                                )
                            }
                        }

                        FloatingActionButton(
                            modifier = Modifier.size(38.dp),
                            onClick = {
                                onItemClicked(it)
                            },
                            backgroundColor = MaterialTheme.colorScheme.tertiaryContainer,
                        ) {
                            Image(
                                modifier = Modifier.size(16.dp),
                                asset = it.icon,
                                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onTertiaryContainer),
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
            backgroundColor = MaterialTheme.colorScheme.secondary,
            onClick = {
                expanded = expanded.not()
            },
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Image(
                asset = MaterialDesignIconic.Icon.gmi_plus,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondary),
            )
        }
    }
}
