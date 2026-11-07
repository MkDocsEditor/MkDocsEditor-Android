package de.markusressel.mkdocsrestclient.document

import com.squareup.moshi.JsonClass
import java.util.*

/**
 * Created by Markus on 03.06.2018.
 */
@JsonClass(generateAdapter = true)
data class DocumentModel(
    val type: String,
    val id: String,
    val name: String,
    val filesize: Long,
    val modtime: Date,
    val url: String,
    /** path relative to the docs folder (backends with references) */
    val path: String? = null,
    /**
     * the page name as MkDocs shows it: the front matter title, else the first level-1 heading, else from the file
     * name. Null from a backend without `page-meta`.
     */
    val title: String? = null,
    /** what the front matter says, null from a backend without `page-meta` */
    val meta: PageMetaModel? = null,
)

/**
 * The fields of the YAML front matter of a page (backend `docs/page-meta.md`). Each is null or empty when the page does
 * not set it.
 *
 * @param icon as written, like `material/rocket`
 * @param status like `new` or `deprecated`
 * @param searchExclude true for `search: {exclude: true}`: the page asks not to be found by search
 */
@JsonClass(generateAdapter = true)
data class PageMetaModel(
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val status: String? = null,
    val tags: List<String> = emptyList(),
    val searchExclude: Boolean = false,
)
