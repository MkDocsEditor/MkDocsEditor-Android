package de.markusressel.mkdocseditor.feature.editor.domain

import android.util.LruCache
import android.webkit.MimeTypeMap
import android.webkit.WebResourceResponse
import androidx.compose.ui.graphics.ImageBitmap
import de.markusressel.mkdocseditor.feature.editor.ui.compose.picker.DocumentTextLoader
import de.markusressel.mkdocseditor.feature.editor.ui.compose.preview.PreviewResourceLoader
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.ThumbnailLoader
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val THUMBNAIL_SIZE_PX = 128
private const val THUMBNAIL_CACHE_SIZE = 200

/**
 * What the pickers and the preview show from other pages and files
 */
internal class EditorResources @Inject constructor(
    restClient: IMkDocsRestClient,
    localDocumentStore: LocalDocumentStore,
    private val previewFileCache: PreviewFileCache,
) {
    /** the text of other documents, for their headings in the link picker */
    val documentTextLoader = DocumentTextLoader { documentId ->
        withContext(Dispatchers.IO) {
            restClient.getDocumentContent(documentId).fold(success = { it }, failure = { null })
                ?: localDocumentStore.load(documentId)?.let { AutomergeTextDocument.load(it.documentState).text }
        }
    }

    private val thumbnails = object : LruCache<String, ImageBitmap>(THUMBNAIL_CACHE_SIZE) {}

    /** small images of resources for the pickers */
    val thumbnailLoader = ThumbnailLoader { resource ->
        val key = "${resource.id}@${resource.modtime.time}"
        thumbnails.get(key) ?: withContext(Dispatchers.IO) {
            previewFileCache.get(resource)?.let { decodeThumbnail(it, THUMBNAIL_SIZE_PX) }
        }?.also { thumbnails.put(key, it) }
    }

    /**
     * Loads the images of the preview: wiki paths are resolved in the tree, the files come
     * from the server with the session, or from the offline cache
     */
    fun previewResourceLoader(tree: () -> WikiTree?) = PreviewResourceLoader { encodedPath ->
        val current = tree() ?: return@PreviewResourceLoader null
        val resource = (current.resolve(current.root, encodedPath) as? WikiItem.Resource)?.resource
            ?: return@PreviewResourceLoader null
        val bytes = runBlocking { previewFileCache.get(resource) } ?: return@PreviewResourceLoader null
        val extension = resource.name.substringAfterLast('.', "").lowercase()
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        WebResourceResponse(mimeType, null, bytes.inputStream())
    }
}
