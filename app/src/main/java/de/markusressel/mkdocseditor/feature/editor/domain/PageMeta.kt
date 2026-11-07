package de.markusressel.mkdocseditor.feature.editor.domain

import com.github.ajalt.timberkt.Timber
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
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

/** @return the title of the front matter, or the name of the file for a page without one */
internal fun editorTitle(meta: PageMeta?, fileName: String): String =
    meta?.title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName
