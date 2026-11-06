package de.markusressel.mkdocseditor.feature.editor.domain

import androidx.annotation.StringRes
import de.markusressel.mkdocseditor.feature.common.ui.userMessage
import android.net.Uri
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.LinkPaths
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import de.markusressel.mkdocseditor.feature.common.data.percentSteps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * @param snippets the Markdown for the files that reached the server
 * @param failure why the upload stopped, if it did. The files before it are in [snippets].
 */
internal data class UploadResult(
    val snippets: List<String>,
    @param:StringRes val failure: Int? = null,
)

/**
 * Uploads files into a section under unique, safe names (large photos are scaled down), and
 * creates the Markdown for them, relative to the document
 */
internal class UploadFilesUseCase(
    private val upload: suspend (
        sectionId: String,
        name: String,
        content: ByteArray,
        onProgress: (doneBytes: Long, totalBytes: Long) -> Unit,
    ) -> Unit,
    private val source: UploadSource,
) {
    @Inject
    constructor(restClient: IMkDocsRestClient, files: EditorFiles) : this(
        { sectionId, name, content, onProgress -> restClient.uploadResource(sectionId, name, content, onProgress).get() },
        files,
    )

    /**
     * Cancelling the coroutine ends the upload, and nothing is inserted
     *
     * @param onProgress how far the upload of the current file is, on a network thread
     */
    suspend operator fun invoke(
        tree: WikiTree,
        document: DocumentData,
        section: SectionData,
        uris: List<Uri>,
        altText: String,
        onProgress: (TransferProgress) -> Unit = {},
    ): UploadResult {
        val created = mutableListOf<String>()
        var failure: Int? = null
        try {
            uris.forEach { uri ->
                val (displayName, bytes) = withContext(Dispatchers.IO) { source.read(uri) }
                val name = PickerMarkdown.uniqueName(
                    section.resources.map { it.name } + created,
                    PickerMarkdown.sanitizeFileName(displayName),
                )
                val content = withContext(Dispatchers.Default) { shrinkImage(name, bytes) }
                val progress = TransferProgress.start(name, upload = true)
                onProgress(progress)
                upload(section.id, name, content, percentSteps { done, total ->
                    onProgress(progress.copy(doneBytes = done, totalBytes = total))
                })
                created += name
                source.onUploaded(uri)
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.e(ex) { "Upload failed" }
            failure = ex.userMessage()
        }

        val fromPath = tree.pathOf(WikiItem.Document(document))
        val sectionPath = tree.pathOf(section)
        val snippets = created.map { name ->
            val path = if (sectionPath.isEmpty()) name else "$sectionPath/$name"
            val image = ResourceKind.ofName(name) == ResourceKind.IMAGE
            val label = PickerMarkdown.escapeLinkText(
                altText.trim().takeIf { created.size == 1 && it.isNotEmpty() }
                    ?: if (image) name.replace(Regex("\\.[^.]+$"), "") else name
            )
            val destination = LinkPaths.linkPath(fromPath, LinkPaths.Target(LinkPaths.TargetType.RESOURCE, path))
            (if (image) "!" else "") + "[$label]($destination)"
        }
        return UploadResult(snippets, failure)
    }
}
