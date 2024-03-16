package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchShareFileIntentUseCase @Inject constructor(
    private val context: Context,
) {
    suspend operator fun invoke(filePath: Path) {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            "de.markusressel.mkdocseditor.fileprovider",
            filePath.toFile()
        )

        fun getMimeType(url: String): String {
            val extension = MimeTypeMap.getFileExtensionFromUrl(url)
            val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            return when {
                type.isNullOrBlank() -> "*/*"
                else -> type
            }
        }

        val mimeType = getMimeType(filePath.toUri().toString())

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startActivity(context, sendIntent, null)
    }
}