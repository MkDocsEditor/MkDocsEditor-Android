package de.markusressel.mkdocseditor.extensions.common

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun File.getMimeType(context: Context): String = toPath().getMimeType(context)

fun Path.getMimeType(context: Context): String = absolutePathString().toUri().getMimeType(context)

fun Uri.getMimeType(context: Context): String {
    return if (ContentResolver.SCHEME_CONTENT == scheme) {
        val cr = context.contentResolver
        cr.getType(this)
    } else {
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
    } ?: "*/*"
}

fun String.getMimeType(): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(this)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return when {
        type.isNullOrBlank() -> "*/*"
        else -> type
    }
}