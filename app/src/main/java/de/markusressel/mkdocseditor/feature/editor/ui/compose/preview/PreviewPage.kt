package de.markusressel.mkdocseditor.feature.editor.ui.compose.preview

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.EditorPalette
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.previewCodeVariables
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

    private val scripts = listOf("mkdocs-renderer.js", "preview.js")
    private val styles = listOf("mkdocs-renderer.css", "preview.css")

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
        return name.takeIf { it in scripts || it in styles }?.let { "preview/$it" }
    }

    fun html(theme: PreviewTheme): String {
        val onSurface = theme.onSurface
        val rgb = listOf(onSurface.red, onSurface.green, onSurface.blue).joinToString(", ") { (it * 255).toInt().toString() }
        val variables = buildString {
            appendLine(":root {")
            appendLine("color-scheme: ${if (theme.dark) "dark" else "light"};")
            appendLine("--background: ${theme.palette.background.toHex()};")
            appendLine("--on-surface: ${onSurface.toHex()};")
            appendLine("--md-on-surface-rgb: $rgb;")
            appendLine("--md-border-color-rgb: $rgb;")
            appendLine("--md-primary: ${theme.primary.toHex()};")
            appendLine("--md-error: ${theme.error.toHex()};")
            appendLine("--md-mark-background: ${if (theme.dark) "#6b5a00" else "#fff3a8"};")
            appendLine(previewCodeVariables(theme.palette))
            append("}")
        }
        return buildString {
            append("<!doctype html><html><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            styles.forEach { append("<link rel=\"stylesheet\" href=\"$ORIGIN$ASSET_PATH$it\">") }
            append("<style>").append(variables).append("</style>")
            append("</head><body><article id=\"content\" class=\"markdown-preview\"></article>")
            scripts.forEach { append("<script src=\"$ORIGIN$ASSET_PATH$it\"></script>") }
            append("</body></html>")
        }
    }
}
