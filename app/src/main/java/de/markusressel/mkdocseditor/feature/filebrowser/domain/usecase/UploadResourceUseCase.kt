package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class UploadResourceUseCase @Inject constructor(
    private val context: Context,
    private val contentResolver: ContentResolver,
    private val restClient: IMkDocsRestClient,
) {
    suspend operator fun invoke(sectionId: String, uri: Uri) {
        val (name, fileContent) = readFile(uri)
        restClient.uploadResource(sectionId, name, fileContent).get()
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