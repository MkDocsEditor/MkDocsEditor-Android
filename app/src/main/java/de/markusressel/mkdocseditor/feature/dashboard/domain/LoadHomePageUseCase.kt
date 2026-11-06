package de.markusressel.mkdocseditor.feature.dashboard.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.filebrowser.data.DocumentData
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocsrestclient.IMkDocsRestClient
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeTextDocument
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** the name of the home page of a MkDocs wiki, without `.md` */
private const val HOME_PAGE_NAME = "index"

/** @return the home page of the wiki: `index.md` in the top folder */
internal fun WikiTree.homePage(): DocumentData? = root.documents.firstOrNull { it.name == HOME_PAGE_NAME }

/**
 * The text of the home page, first as stored and then from the server
 */
internal class LoadHomePageUseCase @Inject constructor(
    private val restClient: IMkDocsRestClient,
    private val localDocumentStore: LocalDocumentStore,
    private val dataRepository: DataRepository,
) {
    /** @return the text on this device: the copy of the editor, which may hold unsynced edits, or the offline cache */
    suspend fun stored(page: DocumentData): String? = withContext(Dispatchers.IO) {
        localDocumentStore.load(page.id)?.let { AutomergeTextDocument.load(it.documentState).text } ?: page.content?.text
    }

    /**
     * @return the text on the server, which is stored for offline use. Null if the server can't be reached, or if the
     * copy of the editor holds edits the server doesn't have yet: those would disappear from the page.
     */
    suspend fun fresh(page: DocumentData): String? = withContext(Dispatchers.IO) {
        if (localDocumentStore.load(page.id)?.hasUnsyncedChanges == true) return@withContext null
        restClient.getDocumentContent(page.id).fold(success = { it }, failure = { null })
            ?.also { dataRepository.updateDocumentContentInCache(page.id, it) }
    }
}
