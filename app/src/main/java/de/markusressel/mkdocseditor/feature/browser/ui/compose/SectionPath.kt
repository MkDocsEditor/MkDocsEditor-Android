package de.markusressel.mkdocseditor.feature.browser.ui.compose

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.browser.ui.usecase.SectionItem

@Preview
@Composable
private fun SectionPathPreview() {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SectionPath(
    path: List<SectionItem>,
    onSectionClicked: (SectionItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previousPathItems: Set<SectionItem> by remember {
        mutableStateOf(emptySet())
    }

    Column(
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier
                .background(color = MaterialTheme.colorScheme.primaryContainer)
                .then(modifier),
        ) {
            (previousPathItems + path).forEach { pathItem ->

                val animatedAlpha by animateFloatAsState(
                    targetValue = when {
                        // item was added just now
                        previousPathItems.contains(pathItem).not() && path.contains(pathItem) -> {
                            previousPathItems = previousPathItems + pathItem
                            0.1F
                        }
                        // item was added before and is "stable"
                        previousPathItems.contains(pathItem) && path.contains(pathItem) -> 1F
                        // item should be removed
                        previousPathItems.contains(pathItem) && path.contains(pathItem).not() -> 0F
                        else -> error("Unexpected case")
                    },
                    finishedListener = {
                        if (it == 0F) {
                            previousPathItems = previousPathItems.minus(pathItem)
                        }
                    }
                )

                Card(
                    modifier = Modifier
                        .alpha(animatedAlpha)
                        .wrapContentWidth()
                        .defaultMinSize(minWidth = 48.dp) // for some reason its not possible to create a card thats less wide than 48.dp
                        .padding(2.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    elevation = CardDefaults.outlinedCardElevation(),
                    shape = RoundedCornerShape(4.dp),
                    onClick = {
                        onSectionClicked(pathItem)
                    },
                ) {
                    Text(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(8.dp),
                        text = pathItem.name,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}
