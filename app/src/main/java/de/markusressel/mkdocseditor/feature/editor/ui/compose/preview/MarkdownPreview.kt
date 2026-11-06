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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import de.markusressel.mkdocseditor.feature.editor.ui.compose.rememberEditorPalette
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewBlock
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLayout
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewLayoutBlock
import de.markusressel.mkdocseditor.feature.editor.domain.PreviewScroll
import de.markusressel.mkdocseditor.feature.editor.domain.SyncAnchor
import de.markusressel.mkdocseditor.feature.editor.ui.compose.ScrollSync
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import org.json.JSONException
import org.json.JSONObject
import com.github.ajalt.timberkt.Timber
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
 * @param onContentHeight with it, the page tells the height of its content in CSS pixels (dp), for a preview that
 * doesn't scroll itself but grows with its content. Zooming is off then.
 * @param syncTarget the source line the page should show, and at which height, read in a snapshot, so that the page
 * moves along when it changes. The page keeps it there when it renders again or changes its size, until the user
 * scrolls it. Null leaves the page where it is.
 * @param onLayout called when the blocks of the page move, after it renders or changes its size
 * @param onScrolled called when the page scrolls, also when the app scrolls it, or when its content moves
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
    onContentHeight: ((Int) -> Unit)? = null,
    syncTarget: (() -> ScrollSync.PreviewTarget?)? = null,
    onLayout: ((PreviewLayout) -> Unit)? = null,
    onScrolled: ((PreviewScroll) -> Unit)? = null,
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
    val currentOnContentHeight by rememberUpdatedState(onContentHeight)
    val currentSyncTarget by rememberUpdatedState(syncTarget)
    val currentOnLayout by rememberUpdatedState(onLayout)
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    val growsWithContent = onContentHeight != null

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
            settings.setSupportZoom(!growsWithContent)
            settings.builtInZoomControls = !growsWithContent
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
                fun contentHeight(height: Int) {
                    mainHandler.post { currentOnContentHeight?.invoke(height) }
                }

                @JavascriptInterface
                fun scrolled(json: String) {
                    val scroll = parsePreviewScroll(json) ?: return
                    mainHandler.post { currentOnScrolled?.invoke(scroll) }
                }

                @JavascriptInterface
                fun layout(json: String) {
                    val layout = parsePreviewLayout(json) ?: return
                    mainHandler.post { currentOnLayout?.invoke(layout) }
                }

                @JavascriptInterface
                fun pageReady() {
                    mainHandler.post {
                        state.onPageReady(currentText, ::renderMarkdown, currentVisible)
                        // a new page starts at the top
                        currentSyncTarget?.invoke()?.let { scrollToLine(it.anchor, smooth = false) }
                    }
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

    LaunchedEffect(webView) {
        snapshotFlow { currentSyncTarget?.invoke() }
            .filterNotNull()
            .collect { webView.scrollToLine(it.anchor, it.smooth) }
    }

    AndroidView(modifier = modifier, factory = { webView })
}

private fun WebView.scrollToLine(anchor: SyncAnchor, smooth: Boolean) {
    // the page may still be loading, and then catches up when it is ready
    evaluateJavascript(
        "window.MkDocsPreview && MkDocsPreview.scrollToLine(${anchor.line}, ${anchor.viewportFraction}, $smooth)",
        null,
    )
}

/** @return null for a report that can't be read */
private fun parsePreviewLayout(json: String): PreviewLayout? = try {
    val report = JSONObject(json)
    val blocks = report.getJSONArray("blocks")
    PreviewLayout(
        blocks = List(blocks.length()) { index ->
            val block = blocks.getJSONArray(index)
            PreviewLayoutBlock(line = block.getInt(0), top = block.getDouble(1).toFloat())
        },
        scrollHeight = report.getDouble("scrollHeight").toFloat(),
    )
} catch (e: JSONException) {
    Timber.w(e) { "The preview reported a layout that can't be read" }
    null
}

/** @return null for a report that can't be read */
private fun parsePreviewScroll(json: String): PreviewScroll? = try {
    val report = JSONObject(json)
    val blocks = report.getJSONArray("blocks")
    PreviewScroll(
        blocks = List(blocks.length()) { index ->
            val block = blocks.getJSONArray(index)
            PreviewBlock(line = block.getInt(0), top = block.getDouble(1).toFloat(), bottom = block.getDouble(2).toFloat())
        },
        height = report.getDouble("height").toFloat(),
        scrollY = report.getDouble("scrollY").toFloat(),
        scrollHeight = report.getDouble("scrollHeight").toFloat(),
        byUser = report.getBoolean("byUser"),
    )
} catch (e: JSONException) {
    Timber.w(e) { "The preview reported a scroll that can't be read" }
    null
}

private fun WebView.renderMarkdown(text: String) {
    evaluateJavascript("MkDocsPreview.render(${JSONObject.quote(text)})", null)
}

private fun notFound() =
    WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
