package de.markusressel.mkdocseditor.service

import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocuments
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** The documents the background upload is responsible for: open ones sync on their own */
@Singleton
internal class PendingUploads @Inject constructor(
    private val store: LocalDocumentStore,
    private val openDocuments: OpenDocuments,
    isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
) {
    private val offlineMode = isOfflineModeEnabledFlowUseCase()

    val flow: Flow<Set<String>> = combine(store.unsyncedIds, openDocuments.idsFlow, offlineMode) { unsynced, open, offline ->
        if (offline) emptySet() else unsynced - open
    }.distinctUntilChanged()

    fun current(): Set<String> = if (offlineMode.value) emptySet() else store.unsyncedIds.value - openDocuments.ids
}
