package de.markusressel.mkdocseditor.feature.editor.domain

import com.github.ajalt.timberkt.Timber
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import de.markusressel.mkdocseditor.feature.filebrowser.data.pageTitle
import java.io.IOException

/**
 * The front matter of a page (the YAML between `---` lines at its start), as the preview renderer reads it. The
 * fields are those MkDocs and Material for MkDocs know; everything else is in [extra].
 *
 * @param icon as written, like `material/emoticon-happy`
 * @param status like `new` or `deprecated`
 * @param hide what the page hides, like `toc`, `navigation` or `tags`
 * @param searchExclude true if the page asks not to be searched
 */
@JsonClass(generateAdapter = true)
internal data class PageMeta(
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    val hide: List<String> = emptyList(),
    val searchExclude: Boolean = false,
    val extra: Map<String, Any?> = emptyMap(),
)

private val pageMetaAdapter: JsonAdapter<PageMeta> by lazy { Moshi.Builder().build().adapter(PageMeta::class.java) }

/** @return null for JSON that can't be read */
internal fun parsePageMeta(json: String): PageMeta? = try {
    pageMetaAdapter.fromJson(json)
} catch (e: JsonDataException) {
    Timber.w(e) { "The preview reported page meta that can't be read" }
    null
} catch (e: IOException) {
    Timber.w(e) { "The preview reported page meta that can't be read" }
    null
}

/**
 * The title in the editor. It is the one the lists show ([storedTitle], from the backend), so a page has one name
 * everywhere. Only the front matter of the text can be newer: the preview reads it while the page is edited, and the
 * tree follows a few seconds later.
 *
 * @param meta the front matter of the text, null before the preview read it
 * @param storedTitle the page name of the stored tree, null from a backend without `page-meta`
 * @param storedMetaTitle the front matter title the stored tree knew
 * @return the title of the front matter, else the stored title, else the name of the file
 */
internal fun editorTitle(
    meta: PageMeta?,
    fileName: String,
    storedTitle: String? = null,
    storedMetaTitle: String? = null,
): String {
    meta?.title?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    // the stored title was the front matter title the text no longer has. Until the tree catches up, the file name
    // is closer than a title that is gone
    if (meta != null && storedMetaTitle != null) return fileName
    return pageTitle(storedTitle, fileName)
}
