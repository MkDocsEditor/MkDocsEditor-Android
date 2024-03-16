package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.getMimeType
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchSendFileIntentUseCase @Inject constructor(
    private val context: Context,
) {
    suspend operator fun invoke(filePath: Path) {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            context.getString(R.string.authorities_fileprovider_id),
            filePath.toFile()
        )

        val mimeType = filePath.getMimeType()

        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, contentUri)
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ContextCompat.startActivity(context, sendIntent, null)
    }
}