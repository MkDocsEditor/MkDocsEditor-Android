package de.markusressel.mkdocseditor.feature.editor.ui.compose.preview

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorPalette
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.previewCodeCss
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.toHex
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree

/**
 * The colors of the preview page
 */
data class PreviewTheme(
    val palette: EditorPalette,
    val onSurface: Color,
    val primary: Color,
    val error: Color,
    val dark: Boolean,
)

/**
 * The HTML page of the markdown preview and the URLs it uses.
 *
 * The page runs on a made up origin. Its base URL is the folder of the document, so relative
 * image paths resolve to wiki paths below that origin; the app answers those requests with the
 * resources of the wiki, loaded with the session. Scripts and styles come from the app assets
 * under [ASSET_PATH].
 */
internal object PreviewPage {
    const val HOST = "mkdocs-preview.invalid"
    const val ORIGIN = "https://$HOST"
    const val ASSET_PATH = "/__preview__/"

    private val scripts = listOf(
        "markdown-it.min.js",
        "markdown-it-footnote.min.js",
        "markdown-it-deflist.min.js",
        "markdown-it-mark.min.js",
        "markdown-it-task-lists.min.js",
        "markdown-it-attrs.js",
        "purify.min.js",
        "highlight.min.js",
        "highlight-extra.min.js",
        "preview.js",
    )

    /**
     * The base URL of a document in the folder [sectionPath] (`a/b`, empty for the root)
     */
    fun baseUrl(sectionPath: String): String {
        val segments = sectionPath.split('/').filter { it.isNotEmpty() }
        return "$ORIGIN/" + segments.joinToString("") { WikiTree.encodeSegment(it) + "/" }
    }

    /**
     * The wiki path a request of the page asks for (still URL encoded, starting with `/`), or
     * null if the request is not for a wiki file
     */
    fun wikiPathOf(host: String?, encodedPath: String?): String? {
        if (host != HOST || encodedPath == null) return null
        if (encodedPath.startsWith(ASSET_PATH)) return null
        return encodedPath
    }

    /** the asset file a request asks for, or null */
    fun assetOf(host: String?, encodedPath: String?): String? {
        if (host != HOST || encodedPath == null || !encodedPath.startsWith(ASSET_PATH)) return null
        val name = encodedPath.removePrefix(ASSET_PATH)
        return name.takeIf { it in scripts || it == "preview.css" }?.let { "preview/$it" }
    }

    fun html(theme: PreviewTheme): String {
        val onSurface = theme.onSurface
        val rgb = listOf(onSurface.red, onSurface.green, onSurface.blue).joinToString(", ") { (it * 255).toInt().toString() }
        val variables = """
            :root {
              color-scheme: ${if (theme.dark) "dark" else "light"};
              --background: ${theme.palette.background.toHex()};
              --on-surface: ${onSurface.toHex()};
              --on-surface-rgb: $rgb;
              --primary: ${theme.primary.toHex()};
              --error: ${theme.error.toHex()};
              --mark: ${if (theme.dark) "#6b5a00" else "#fff3a8"};
            }
        """.trimIndent()
        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<link rel=\"stylesheet\" href=\"$ORIGIN${ASSET_PATH}preview.css\">")
            append("<style>").append(variables).append('\n').append(previewCodeCss(theme.palette)).append("</style>")
            append("</head><body><article id=\"content\"></article>")
            scripts.forEach { append("<script src=\"$ORIGIN$ASSET_PATH$it\"></script>") }
            append("</body></html>")
        }
    }
}
