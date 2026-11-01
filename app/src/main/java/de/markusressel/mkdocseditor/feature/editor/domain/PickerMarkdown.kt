package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.data.resourceKind
import java.text.Normalizer

/**
 * The Markdown the link and image pickers insert, the same as the web client
 * (`src/components/pickers/markdown.ts`)
 */
object PickerMarkdown {

    /** link text without characters that would end it */
    fun escapeLinkText(text: String): String = text.replace(Regex("([\\[\\]\\\\])"), "\\\\$1")

    /** URLs typed by hand: spaces and parentheses would end the destination */
    fun cleanUrl(url: String): String = url.trim().replace(" ", "%20").replace("(", "%28").replace(")", "%29")

    /**
     * The default text of a link to an item (like the backend's `GET /link/`): the name,
     * without the extension for an embedded image's alt text
     */
    fun defaultLinkText(item: WikiItem, embedded: Boolean = false): String =
        if (embedded) item.name.replace(Regex("\\.[^.]+$"), "") else item.name

    /**
     * Markdown for a link from the document at [fromPath] to [item]: `![alt](dest)` for images
     * (unless [asLink]), `[text](dest)` otherwise
     */
    fun markdownLink(
        tree: WikiTree,
        fromPath: String,
        item: WikiItem,
        text: String = "",
        anchor: String = "",
        asLink: Boolean = false,
    ): String {
        val destination = tree.linkPath(fromPath, item, anchor)
        val image = !asLink && item is WikiItem.Resource && item.resource.resourceKind() == ResourceKind.IMAGE
        val label = escapeLinkText(text.trim().ifEmpty { defaultLinkText(item, image) })
        return (if (image) "!" else "") + "[$label]($destination)"
    }

    /** a link to a web address */
    fun webLink(url: String, text: String): String =
        "[${escapeLinkText(text.trim().ifEmpty { url.trim() })}](${cleanUrl(url)})"

    /** an image from a web address; the alt text defaults to the file name in the address */
    fun webImage(url: String, alt: String): String {
        val fallback = url.trim().substringBefore('#').substringBefore('?').split('/').lastOrNull { it.isNotEmpty() } ?: "image"
        return "![${escapeLinkText(alt.trim().ifEmpty { fallback })}](${cleanUrl(url)})"
    }

    /** true when the text looks like a web address */
    fun isWebAddress(text: String): Boolean = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE).containsMatchIn(text.trim())

    /** a safe file name for an upload */
    fun sanitizeFileName(name: String): String {
        val cleaned = Normalizer.normalize(name, Normalizer.Form.NFC)
            .replace(Regex("[/\\\\:*?\"<>|]+"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
            .replace(Regex("^\\.+"), "")
        return cleaned.ifEmpty { "file" }
    }

    /** a name that does not exist in the folder yet: `Foto.jpg`, `Foto (2).jpg`, … */
    fun uniqueName(taken: Collection<String>, name: String): String {
        val lower = taken.map { it.lowercase() }.toSet()
        if (name.lowercase() !in lower) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot + 1) else ""
        val base = if (ext.isNotEmpty()) name.substring(0, dot) else name
        var i = 2
        while (true) {
            val candidate = if (ext.isNotEmpty()) "$base ($i).$ext" else "$base ($i)"
            if (candidate.lowercase() !in lower) return candidate
            i++
        }
    }
}
