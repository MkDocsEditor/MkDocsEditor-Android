package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import de.markusressel.mkdocseditor.feature.dashboard.data.forgetDashboards
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
        val used = directories.all() + directory
        scope.launch(Dispatchers.IO) { deleteUnused(used) }
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

    /** @return how many pages hold edits that removing this server would lose */
    @Synchronized
    fun unsyncedPages(backendId: Long, origin: String?): Int {
        val own = directories.directoriesOf(backendId).filter { it != ScopeDirectories.EXISTING }
            .sumOf { documentStoreOf(it).unsyncedOf(null).size }
        // the existing storage is older than this class and can hold copies of several servers
        val existing = origin?.let { documentStoreOf(ScopeDirectories.EXISTING).unsyncedOf(it).size } ?: 0
        return own + existing
    }

    /**
     * Deletes the local data of a removed server, because nothing can reach it afterwards: adding the server again starts
     * with a new directory. An open database can't be deleted, so its files go at the next start, see [deleteUnused].
     */
    @Synchronized
    fun forget(backendId: Long, origin: String?) {
        directories.forget(backendId).forEach { removed ->
            val store = storeOf(removed)
            store.runInTx {
                store.boxFor<DocumentContentEntity>().removeAll()
                store.boxFor<DocumentLayoutEntity>().removeAll()
                store.boxFor<ResourceEntity>().removeAll()
                store.boxFor<DocumentEntity>().removeAll()
                store.boxFor<SectionEntity>().removeAll()
            }
            if (removed != ScopeDirectories.EXISTING) documentStoreOf(removed).deleteAll()
        }
        origin?.let { documentStoreOf(ScopeDirectories.EXISTING).deleteCopiesOf(it) }
        forgetDashboards(context, backendId)
    }

    /** Removes what [forget] could not, or what a late save of an open page wrote afterwards */
    private fun deleteUnused(used: Set<String>) {
        context.filesDir.listFiles { file -> file.name.startsWith(COPIES_PREFIX) }
            ?.filter { it.name.removePrefix(COPIES_PREFIX) !in used }
            ?.forEach { it.deleteRecursively() }
        File(context.filesDir, "objectbox").listFiles { file -> file.name.startsWith(DATABASE_PREFIX) }
            ?.filter { it.name.removePrefix(DATABASE_PREFIX) !in used }
            ?.forEach { BoxStore.deleteAllFiles(it) }
    }

    private fun selectedDirectory(): String? {
        val selected = mainStore.boxFor<BackendConfigEntity>().query(BackendConfigEntity_.isSelected.equal(true)).build()
            .use { it.findFirst() } ?: return null
        return directories.directoryOf(selected.entityId, authRepository.session(selected.entityId)?.username)
    }

    private fun storeOf(directory: String): BoxStore = stores.getOrPut(directory) {
        if (directory == ScopeDirectories.EXISTING) mainStore
        else MyObjectBox.builder().androidContext(context).name("$DATABASE_PREFIX$directory").build()
    }

    private fun documentStoreOf(directory: String): FileDocumentStore = documentStores.getOrPut(directory) {
        val name = if (directory == ScopeDirectories.EXISTING) "automerge" else "$COPIES_PREFIX$directory"
        FileDocumentStore(File(context.filesDir, name))
    }
}

private const val DATABASE_PREFIX = "wiki-"
private const val COPIES_PREFIX = "automerge-"

private class PreferencesScopeTable(private val preferences: SharedPreferences) : ScopeTable {
    override fun get(key: String): String? = preferences.getString(key, null)
    override fun set(key: String, value: String) = preferences.edit().putString(key, value).apply()
    override fun remove(key: String) = preferences.edit().remove(key).apply()
    override fun keys(): Set<String> = preferences.all.keys
}
