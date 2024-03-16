package de.markusressel.mkdocseditor.feature.browser.domain.usecase

import android.app.DownloadManager
import de.markusressel.mkdocseditor.storage.AppStorageManager
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DownloadResourceUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val downloadManager: DownloadManager,
    private val appStorageManager: AppStorageManager,
) {
    suspend operator fun invoke(resourceId: String, name: String): Path {
//        val request = DownloadManager.Request(resourceId).apply {
//            setTitle("Downloading '$name'...")
//            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
//            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
//        }
//        downloadManager.enqueue(request)
//
//        val pdfUri = downloadManager.getUriForDownloadedFile(downloadID)

        val result = restClient.downloadResource(resourceId).get()
        return appStorageManager.writeToFile(name, result)
    }
}