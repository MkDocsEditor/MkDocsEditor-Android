package de.markusressel.mkdocseditor.extensions.common

import android.webkit.MimeTypeMap
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

fun Path.getMimeType(): String = absolutePathString().getMimeType()

fun File.getMimeType(): String = absolutePath.getMimeType()

fun String.getMimeType(): String {
    val extension = MimeTypeMap.getFileExtensionFromUrl(this)
    val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    return when {
        type.isNullOrBlank() -> "*/*"
        else -> type
    }
}