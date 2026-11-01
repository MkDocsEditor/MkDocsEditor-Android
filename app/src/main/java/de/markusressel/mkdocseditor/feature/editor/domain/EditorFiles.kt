package de.markusressel.mkdocseditor.feature.editor.domain

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.PhotoUriFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Where [UploadFilesUseCase] gets its files from
 */
internal interface UploadSource {
    /** the name and content of a file chosen for an upload */
    fun read(uri: Uri): Pair<String, ByteArray>

    /** the file reached the server */
    fun onUploaded(uri: Uri)
}

/**
 * The files of the device for uploads: chosen files, and photos taken for an upload
 */
internal class EditorFiles @Inject constructor(
    private val context: Context,
    private val contentResolver: ContentResolver,
) : UploadSource {

    /** content URIs for photos taken from the image picker */
    val photoUriFactory = PhotoUriFactory {
        val directory = File(context.cacheDir, "camera").apply { mkdirs() }
        val name = "photo-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ENGLISH).format(Date()) + ".jpg"
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(directory, name))
    }

    override fun read(uri: Uri): Pair<String, ByteArray> {
        val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val bytes = requireNotNull(contentResolver.openInputStream(uri)) { "Could not read $uri" }.use { it.readBytes() }
        return name to bytes
    }

    /** photos taken for an upload are not needed after it */
    override fun onUploaded(uri: Uri) {
        if (uri.authority != "${context.packageName}.fileprovider") return
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: return
        File(File(context.cacheDir, "camera"), name).takeIf { it.isFile }?.delete()
    }
}
