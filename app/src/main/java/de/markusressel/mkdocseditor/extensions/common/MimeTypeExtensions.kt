package de.markusressel.mkdocseditor.extensions.common

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap

fun Uri.getMimeType(context: Context): String {
    return if (ContentResolver.SCHEME_CONTENT == scheme) {
        val cr = context.contentResolver
        cr.getType(this)
    } else {
        val fileExtension = MimeTypeMap.getFileExtensionFromUrl(toString())
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.lowercase())
    } ?: "*/*"
}