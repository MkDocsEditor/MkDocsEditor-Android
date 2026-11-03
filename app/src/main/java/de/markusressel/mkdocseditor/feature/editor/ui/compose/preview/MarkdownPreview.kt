package de.markusressel.mkdocseditor.feature.editor.ui.compose.preview

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.markusressel.mkdocseditor.feature.editor.ui.compose.rememberEditorPalette
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Loads the files of the wiki that the preview shows (images), by their wiki path
 */
fun interface PreviewResourceLoader {
    /**
     * Called on a background thread.
     *
     * @param encodedPath the URL encoded wiki path, starting with `/`
     * @return the file, or null if it does not exist or can't be loaded
     */
    fun load(encodedPath: String): WebResourceResponse?
}

/**
 * A live preview of markdown, rendered on the device like in the web client, so it works
 * offline and shows unsaved text.
 *
 * @param text the markdown to show
 * @param sectionPath the folder of the document (`a/b`, empty for the root), for relative paths
 * @param resourceLoader loads the wiki files that the page asks for
 * @param onLinkClicked called with the target of a clicked link, as written in the markdown
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MarkdownPreview(
    text: String,
    sectionPath: String,
    resourceLoader: PreviewResourceLoader,
    onLinkClicked: (String) -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val palette = rememberEditorPalette()
    val colorScheme = MaterialTheme.colorScheme
    val theme = PreviewTheme(
        palette = palette,
        onSurface = colorScheme.onSurface,
        primary = colorScheme.primary,
        error = colorScheme.error,
        dark = colorScheme.background.luminance() < 0.5f,
    )

    val currentText by rememberUpdatedState(text)
    val currentLoader by rememberUpdatedState(resourceLoader)
    val currentOnLinkClicked by rememberUpdatedState(onLinkClicked)
    val currentVisible by rememberUpdatedState(visible)

    val state = remember { PreviewRenderState() }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            // only the pinch, without the buttons on top of the page
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val url = request.url
                    PreviewPage.assetOf(url.host, url.encodedPath)?.let { asset ->
                        return WebResourceResponse(
                            if (asset.endsWith(".css")) "text/css" else "application/javascript",
                            "utf-8",
                            view.context.assets.open(asset),
                        )
                    }
                    PreviewPage.wikiPathOf(url.host, url.encodedPath)?.let { path ->
                        return currentLoader.load(path) ?: notFound()
                    }
                    // images from the internet load as usual
                    return null
                }

                // links are handled by the page script; nothing navigates the page away
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
            }
            // without room a WebView that never had any paints over the views around it
            addOnLayoutChangeListener { view, left, top, right, bottom, _, _, _, _ ->
                view.visibility = if (right > left && bottom > top) View.VISIBLE else View.INVISIBLE
            }
            val mainHandler = Handler(Looper.getMainLooper())
            addJavascriptInterface(object {
                @JavascriptInterface
                fun openLink(href: String) {
                    mainHandler.post { currentOnLinkClicked(href) }
                }

                @JavascriptInterface
                fun pageReady() {
                    mainHandler.post { state.onPageReady(currentText, ::renderMarkdown) }
                }
            }, "MkDocsPreviewBridge")
        }
    }

    DisposableEffect(webView) {
        onDispose {
            state.dispose()
            webView.destroy()
        }
    }

    // a new page for another folder or theme; the text follows when it has loaded
    LaunchedEffect(sectionPath, theme) {
        state.onPageLoading()
        webView.loadDataWithBaseURL(
            PreviewPage.baseUrl(sectionPath),
            PreviewPage.html(theme),
            "text/html",
            "utf-8",
            null,
        )
    }

    // re-render at most every 150 ms while typing
    LaunchedEffect(text) {
        delay(150)
        if (currentVisible) state.show(text, webView::renderMarkdown)
    }

    // nobody sees the page while typing without a split, so it catches up when it comes back
    LaunchedEffect(visible) {
        if (visible) state.show(currentText, webView::renderMarkdown)
    }

    AndroidView(modifier = modifier, factory = { webView })
}

private fun WebView.renderMarkdown(text: String) {
    evaluateJavascript("MkDocsPreview.render(${JSONObject.quote(text)})", null)
}

private fun notFound() =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
