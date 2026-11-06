package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import de.markusressel.mkdocseditor.feature.common.data.percentSteps
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UploadResourceUseCase @Inject constructor(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val restClient: IMkDocsRestClient,
) {
    /**
     * Cancelling the coroutine ends the upload
     *
     * @param onProgress how far the upload is, on a network thread
     */
    suspend operator fun invoke(sectionId: String, uri: Uri, onProgress: (TransferProgress) -> Unit = {}) {
        val (name, fileContent) = withContext(Dispatchers.IO) { readFile(uri) }
        val progress = TransferProgress.start(name, upload = true)
        onProgress(progress)
        restClient.uploadResource(sectionId, name, fileContent, percentSteps { done, total ->
            onProgress(progress.copy(doneBytes = done, totalBytes = total))
        }).get()
    }

    private fun readFile(uri: Uri): Pair<String, ByteArray> {
        val documentFile =
            requireNotNull(DocumentFile.fromSingleUri(context, uri)) { "Could not get document file for uri $uri" }
        if (documentFile.isFile.not()) {
            throw IllegalArgumentException("Uri $uri does not point to a file")
        }
        val inputStream = contentResolver.openInputStream(documentFile.uri)
        requireNotNull(inputStream) { "Could not get input stream for uri $uri" }

        val name = documentFile.name ?: throw IllegalArgumentException("Uri $uri does not have a name")
        val content = inputStream.use(InputStream::readBytes)

        return name to content
    }
}