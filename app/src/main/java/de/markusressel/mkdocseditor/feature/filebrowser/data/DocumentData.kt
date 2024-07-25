package de.markusressel.mkdocseditor.feature.filebrowser.data

import android.content.Context
import android.text.format.Formatter
import java.util.Date

data class DocumentData(
    val entityId: Long,
    val id: String,
    val name: String,
    val filesize: Long,
    val modtime: Date,
    val url: String,
    val content: DocumentContentData?,
    val isOfflineAvailable: Boolean
) {
    /**
     * Human readable representation of file size
     */
    fun formattedDocumentSize(context: Context): String {
        return Formatter.formatFileSize(context, filesize)
    }
}