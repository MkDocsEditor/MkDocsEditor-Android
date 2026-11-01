package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocsrestclient.AuthorizationProvider
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.SyncedDocument
import javax.inject.Inject

internal class SyncedDocumentFactory @Inject constructor(
    private val authRepository: AuthRepository,
    private val localDocumentStore: LocalDocumentStore,
) {
    /** Reads and parses the local copy, so this is not called on the main thread */
    fun create(
        documentId: String,
        serverConfig: BackendServerConfig,
        backendId: Long,
        listener: AutomergeDocumentSyncManager.Listener,
    ): SyncedDocument = AutomergeDocumentSyncManager(
        hostname = serverConfig.domain,
        port = serverConfig.port,
        ssl = serverConfig.useSsl,
        authorizationProvider = AuthorizationProvider.bearer { authRepository.token(backendId) },
        documentId = documentId,
        store = localDocumentStore,
        listener = listener,
    )
}
