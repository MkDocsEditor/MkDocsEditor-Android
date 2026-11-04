package de.markusressel.mkdocsrestclient.sync.automerge

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/** The local copies of the selected server and account, see [switchTo] */
class ScopedDocumentStore(initial: LocalDocumentStore, scope: CoroutineScope) : LocalDocumentStore {

    private val _current = MutableStateFlow(initial)
    private val current: LocalDocumentStore
        get() = _current.value

    @OptIn(ExperimentalCoroutinesApi::class)
    override val unsyncedIds: StateFlow<Set<String>> = _current
        .flatMapLatest { it.unsyncedIds }
        .stateIn(scope, SharingStarted.Eagerly, initial.unsyncedIds.value)

    fun switchTo(store: LocalDocumentStore) {
        _current.value = store
    }

    override fun pinned(): LocalDocumentStore = current
    override fun load(documentId: String) = current.load(documentId)
    override fun contains(documentId: String) = current.contains(documentId)
    override fun save(documentId: String, document: StoredDocument) = current.save(documentId, document)
    override fun scanUnsynced() = current.scanUnsynced()
    override fun ids() = current.ids()
    override fun move(fromDocumentId: String, toDocumentId: String) = current.move(fromDocumentId, toDocumentId)
    override fun keepAside(documentId: String) = current.keepAside(documentId)
}
