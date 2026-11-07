package de.markusressel.mkdocseditor.feature.filebrowser.data

import android.content.Context
import android.text.format.Formatter
import java.util.Date

/**
 * @param name the file name without `.md`
 * @param title the page name from the backend (front matter title, first heading or file name), null from a backend
 * without `page-meta`
 * @param metaTitle the title of the front matter only
 * @param searchExclude the page asks not to be found by search
 */
data class DocumentData(
    val entityId: Long,
    val id: String,
    val name: String,
    val filesize: Long,
    val modtime: Date,
    val url: String,
    val content: DocumentContentData?,
    val isOfflineAvailable: Boolean,
    val title: String? = null,
    val metaTitle: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val searchExclude: Boolean = false,
) {
    /** what lists show as the name of the page */
    val shownTitle: String
        get() = pageTitle(title, name)

    /**
     * the file name, when it says something the title does not. A title the backend made from the file name
     * (`my-page` is `My page`) says the same.
     */
    val secondaryName: String?
        get() = name.takeIf { !it.replace('-', ' ').replace('_', ' ').equals(shownTitle, ignoreCase = true) }

    /**
     * Human readable representation of file size
     */
    fun formattedDocumentSize(context: Context): String {
        return Formatter.formatFileSize(context, filesize)
    }
}

/** @return the title of a page from the backend, or [fileName] from a backend that sends none */
internal fun pageTitle(title: String?, fileName: String): String = title?.trim()?.takeIf { it.isNotEmpty() } ?: fileName