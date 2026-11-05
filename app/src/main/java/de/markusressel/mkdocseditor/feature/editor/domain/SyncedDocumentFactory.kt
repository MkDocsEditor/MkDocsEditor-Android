package de.markusressel.mkdocseditor.feature.editor.domain

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.websocketBaseUrl
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
        /** only an editor is an instance, a background upload shows up nowhere */
        instanceId: String? = null,
    ): SyncedDocument = AutomergeDocumentSyncManager(
        websocketBaseUrl = serverConfig.websocketBaseUrl,
        // stored copies carry this, so it has to stay as it is even for an address that isn't clean
        origin = AutomergeDocumentSyncManager.originOf(serverConfig.domain, serverConfig.port),
        authorizationProvider = AuthorizationProvider.bearer { authRepository.token(backendId) },
        documentId = documentId,
        store = localDocumentStore.pinned(),
        listener = listener,
        instanceId = instanceId,
    )
}
