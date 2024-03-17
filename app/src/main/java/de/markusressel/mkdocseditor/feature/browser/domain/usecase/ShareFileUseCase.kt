package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.getMimeType
import de.markusressel.mkdocseditor.extensions.common.startsWithAny
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareFileUseCase @Inject constructor(
    private val context: Context,
    private val launchOpenInIntentUseCase: LaunchOpenInIntentUseCase,
    private val launchSendFileIntentUseCase: LaunchSendFileIntentUseCase,
) {
    suspend operator fun invoke(filePath: Path) {
        val contentUri: Uri = FileProvider.getUriForFile(
            context,
            context.getString(R.string.authorities_fileprovider_id),
            filePath.toFile()
        )

        val mimeType = contentUri.getMimeType(context)

        if (mimeType.startsWithAny("image/", "video/", "audio/")) {
            launchOpenInIntentUseCase(contentUri, mimeType)
        } else {
            launchSendFileIntentUseCase(contentUri, mimeType)
        }
    }
}
