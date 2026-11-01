package de.markusressel.mkdocseditor.feature.editor.domain

import android.net.Uri
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.LinkPaths
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.data.SectionData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * @param snippets the Markdown for the files that reached the server
 * @param failure why the upload stopped, if it did. The files before it are in [snippets].
 */
internal data class UploadResult(
    val snippets: List<String>,
    val failure: String? = null,
)

/**
 * Uploads files into a section under unique, safe names (large photos are scaled down), and
 * creates the Markdown for them, relative to the document
 */
internal class UploadFilesUseCase(
    private val upload: suspend (sectionId: String, name: String, content: ByteArray) -> Unit,
    private val source: UploadSource,
) {
    @Inject
    constructor(restClient: IMkDocsRestClient, files: EditorFiles) : this(
        { sectionId, name, content -> restClient.uploadResource(sectionId, name, content).get() },
        files,
    )

    suspend operator fun invoke(
        tree: WikiTree,
        document: DocumentData,
        section: SectionData,
        uris: List<Uri>,
        altText: String,
    ): UploadResult {
        val created = mutableListOf<String>()
        var failure: String? = null
        try {
            uris.forEach { uri ->
                val (displayName, bytes) = withContext(Dispatchers.IO) { source.read(uri) }
                val name = PickerMarkdown.uniqueName(
                    section.resources.map { it.name } + created,
                    PickerMarkdown.sanitizeFileName(displayName),
                )
                val content = withContext(Dispatchers.Default) { shrinkImage(name, bytes) }
                upload(section.id, name, content)
                created += name
                source.onUploaded(uri)
            }
        } catch (ex: Exception) {
            Timber.e(ex) { "Upload failed" }
            failure = ex.message ?: ex.javaClass.simpleName
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
