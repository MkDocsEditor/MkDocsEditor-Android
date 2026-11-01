package de.markusressel.mkdocseditor.feature.editor.domain

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import javax.inject.Inject

/** where a link in the preview leads */
internal sealed interface PreviewLinkTarget {
    data class External(val href: String) : PreviewLinkTarget
    data class Document(val documentId: String) : PreviewLinkTarget
    data class Section(val sectionId: String) : PreviewLinkTarget
    data class Resource(val resourceId: String, val name: String) : PreviewLinkTarget
    data object NotFound : PreviewLinkTarget
}

internal object PreviewLinks {
    /**
     * @param tree the wiki tree, if it is loaded
     * @param documentId the open document
     * @return null when wiki links can not be resolved yet
     */
    fun resolve(tree: WikiTree?, documentId: String?, href: String): PreviewLinkTarget? {
        if (WikiTree.isExternal(href)) return PreviewLinkTarget.External(href)
        documentId ?: return null
        tree ?: return null
        val from = tree.parentOfDocument(documentId) ?: tree.root
        return when (val target = tree.resolve(from, href)) {
            is WikiItem.Document -> PreviewLinkTarget.Document(target.id)
            is WikiItem.Section -> PreviewLinkTarget.Section(target.id)
            is WikiItem.Resource -> PreviewLinkTarget.Resource(target.id, target.name)
            null -> PreviewLinkTarget.NotFound
        }
    }
}

/**
 * Opens links that lead out of the wiki
 */
internal class ExternalLinkOpener @Inject constructor(
    private val chromeCustomTabManager: ChromeCustomTabManager,
    private val context: Context,
) {
    /** @return false if nothing could open the link */
    fun open(href: String): Boolean = try {
        if (href.startsWith("http://") || href.startsWith("https://")) {
            chromeCustomTabManager.openChromeCustomTab(href)
        } else {
            val url = if (href.startsWith("//")) "https:$href" else href
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        true
    } catch (ex: Exception) {
        Timber.e(ex)
        false
    }
}
