package de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase

import android.app.DownloadManager
import de.markusressel.mkdocseditor.storage.AppStorageManager
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import de.markusressel.mkdocseditor.feature.common.data.percentSteps
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.CancellationException
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.exists


@Singleton
class DownloadResourceUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val downloadManager: DownloadManager,
    private val appStorageManager: AppStorageManager,
) {
    /**
     * Cancelling the coroutine ends the download
     *
     * @param onProgress how far the download is, on a network thread
     */
    suspend operator fun invoke(resourceId: String, name: String, onProgress: (TransferProgress) -> Unit = {}): Path {
//        val request = DownloadManager.Request(resourceId).apply {
//            setTitle("Downloading '$name'...")
//            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
//        }
//        downloadManager.enqueue(request)
//
//        val pdfUri = downloadManager.getUriForDownloadedFile(downloadID)

        val existingFile = appStorageManager.getFile(resourceId, name)

        return try {
            val progress = TransferProgress.start(name, upload = false)
            onProgress(progress)
            val data = restClient.downloadResource(resourceId, percentSteps { done, total ->
                onProgress(progress.copy(doneBytes = done, totalBytes = total))
            }).get()
            appStorageManager.writeToFile(
                id = resourceId,
                name = name,
                data = data,
                override = true,
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            if (existingFile.exists()) {
                return existingFile
            }
            throw ex
        }
    }
}