package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.data.persistence.base.WikiDatabase
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity
import de.markusressel.mkdocseditor.data.persistence.entity.BackendConfigEntity_
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentContentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentLayoutEntity
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocseditor.data.persistence.entity.ResourceEntity
import de.markusressel.mkdocseditor.data.persistence.entity.SectionEntity
import de.markusressel.mkdocsrestclient.sync.automerge.FileDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.ScopedDocumentStore
import io.objectbox.BoxStore
import io.objectbox.kotlin.boxFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Points the wiki data at the selected server and signed-in account. Selecting a server reads no flow, so nothing can
 * read the data of the previous server after [refresh] returned.
 */
@Singleton
class WikiScope @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mainStore: BoxStore,
    private val authRepository: AuthRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val directories = ScopeDirectories(PreferencesScopeTable(context.getSharedPreferences("wiki_scopes", Context.MODE_PRIVATE)))
    private val stores = mutableMapOf<String, BoxStore>()
    private val documentStores = mutableMapOf<String, FileDocumentStore>()

    private var directory = selectedDirectory() ?: ScopeDirectories.EXISTING

    val database = WikiDatabase(storeOf(directory))
    val documents = ScopedDocumentStore(documentStoreOf(directory), scope)

    init {
        scope.launch { authRepository.sessionChanges.collect { refresh() } }
    }

    /** Follows the selected server and account. Without a selected server the data stays where it is. */
    @Synchronized
    fun refresh() {
        val selected = selectedDirectory() ?: return
        if (selected == directory) return
        directory = selected
        database.switchTo(storeOf(selected))
        val documentStore = documentStoreOf(selected)
        documents.switchTo(documentStore)
        // edits made earlier on this server show as unsynced again, which uploads them
        scope.launch(Dispatchers.IO) { documentStore.scanUnsynced() }
    }

    /** A removed server has no tree to show anymore. Its local copies stay on disk, as they may hold unsynced edits. */
    @Synchronized
    fun forget(backendId: Long) {
        directories.forget(backendId).forEach { removed ->
            val store = storeOf(removed)
            store.runInTx {
                store.boxFor<DocumentContentEntity>().removeAll()
                store.boxFor<DocumentLayoutEntity>().removeAll()
                store.boxFor<ResourceEntity>().removeAll()
                store.boxFor<DocumentEntity>().removeAll()
                store.boxFor<SectionEntity>().removeAll()
            }
        }
    }

    private fun selectedDirectory(): String? {
        val selected = mainStore.boxFor<BackendConfigEntity>().query(BackendConfigEntity_.isSelected.equal(true)).build()
            .use { it.findFirst() } ?: return null
        return directories.directoryOf(selected.entityId, authRepository.session(selected.entityId)?.username)
    }

    private fun storeOf(directory: String): BoxStore = stores.getOrPut(directory) {
        if (directory == ScopeDirectories.EXISTING) mainStore
        else MyObjectBox.builder().androidContext(context).name("wiki-$directory").build()
    }

    private fun documentStoreOf(directory: String): FileDocumentStore = documentStores.getOrPut(directory) {
        val name = if (directory == ScopeDirectories.EXISTING) "automerge" else "automerge-$directory"
        FileDocumentStore(File(context.filesDir, name))
    }
}

private class PreferencesScopeTable(private val preferences: SharedPreferences) : ScopeTable {
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun set(key: String, value: String) = preferences.edit().putString(key, value).apply()
    override fun remove(key: String) = preferences.edit().remove(key).apply()
    override fun keys(): Set<String> = preferences.all.keys
}
