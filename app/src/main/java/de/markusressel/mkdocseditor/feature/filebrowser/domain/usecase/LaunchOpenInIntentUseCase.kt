package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LaunchOpenInIntentUseCase @Inject constructor(
    private val context: Context,
) {
    operator fun invoke(contentUri: Uri, mimeType: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_DEFAULT
            type = mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            setDataAndType(contentUri, mimeType)
        }
        ContextCompat.startActivity(context, sendIntent, null)
    }
}