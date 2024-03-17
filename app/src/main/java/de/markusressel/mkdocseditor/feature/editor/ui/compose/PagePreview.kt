package de.markusressel.mkdocseditor.feature.editor.ui.compose

import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.Flow

sealed class WebViewAction {
    data object Refresh : WebViewAction()
}

@Composable
internal fun PagePreview(
    modifier: Modifier = Modifier,
    url: String,
    actions: Flow<WebViewAction>,
) {
    val context = LocalContext.current
    val webView = remember { WebView(context) }

    LaunchedEffect(Unit) {
        actions.collect { action ->
            when (action) {
                is WebViewAction.Refresh -> {
                    webView.reload()
                }
            }
        }
    }

    // Adding a WebView inside AndroidView
    // with layout as full screen
    AndroidView(
        modifier = modifier,
        factory = {
            webView.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = {
            it.loadUrl(url)
        }
    )
}