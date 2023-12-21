package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring.StiffnessMedium
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.fragment.base.FabConfig
import de.markusressel.mkdocseditor.util.compose.CombinedPreview

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
        val density = LocalDensity.current
        AnimatedVisibility(
            modifier = Modifier.zIndex(90F),
            visible = expanded,
            enter = fadeIn() + slideInVertically {
                with(density) { 20.dp.roundToPx() }
            },
            exit = fadeOut(
                animationSpec = spring(stiffness = StiffnessMedium)
            ) + slideOutVertically {
                with(density) { 20.dp.roundToPx() }
            },
        ) {
            Column(
                modifier = Modifier.padding(end = 8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                items.forEach {
                    Row(
                        modifier = Modifier.clickable {
                            onItemClicked(it)
                            expanded = false
                        },
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
                                expanded = false
                            },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
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

        val animatedRotation by animateFloatAsState(
            targetValue = when {
                expanded -> 360F + 45F
                else -> 0F
            },
            label = "rotation"
        )

        // main button to toggle options
        FloatingActionButton(
            modifier = Modifier.zIndex(100F),
            containerColor = MaterialTheme.colorScheme.secondary,
            onClick = {
                if (items.size <= 1) {
                    onItemClicked(items.first())
                } else {
                    expanded = expanded.not()
                }
            },
            contentColor = MaterialTheme.colorScheme.onSecondary
        ) {
            Image(
                modifier = Modifier
                    .size(24.dp)
                    .rotate(animatedRotation),
                asset = MaterialDesignIconic.Icon.gmi_plus,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSecondary),
            )
        }
    }
}


@CombinedPreview
@Composable
private fun ExpandableFabPreview() {
    MkDocsEditorTheme {
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
}

