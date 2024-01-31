package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.util.compose.CombinedPreview
import kotlinx.coroutines.delay

@Composable
fun LoadingOverlay(
    modifier: Modifier,
    isLoading: Boolean,
    loadingContent: @Composable () -> Unit = {
        CircularProgressIndicator()
    },
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        content()

        AnimatedVisibility(
            modifier = Modifier
                .zIndex(100F)
                .matchParentSize(),
            visible = isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        // consume touch input
                    }
                    .background(
                        color = Color.Black.copy(alpha = 0.3F)
                    ),
                contentAlignment = Alignment.Center
            ) {
                loadingContent()
            }
        }
    }
}

@CombinedPreview
@Composable
private fun LoadingOverlayPreview() {
    var isLoading by remember {
        mutableStateOf(true)
    }

    LaunchedEffect(Unit) {
        while (true) {
            isLoading = !isLoading
            delay(2000)
        }
    }

    MkDocsEditorTheme {
        LoadingOverlay(
            modifier = Modifier.fillMaxSize(),
            isLoading = isLoading,
        ) {
            Text(text = "Content")
        }
    }
}