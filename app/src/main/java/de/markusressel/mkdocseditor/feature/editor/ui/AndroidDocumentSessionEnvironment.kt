package de.markusressel.mkdocseditor.feature.editor.ui

import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.GetDocumentUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.OpenDocuments
import de.markusressel.mkdocseditor.feature.editor.domain.SyncedDocumentFactory
import de.markusressel.mkdocseditor.feature.filebrowser.data.DataRepository
import de.markusressel.mkdocseditor.feature.preferences.data.KutePreferencesHolder
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.network.domain.IsOfflineModeEnabledFlowUseCase
import de.markusressel.mkdocseditor.network.newValues
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.LocalCopyRedirects
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.SyncedDocument
import de.markusressel.mkdocseditor.util.Resource
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class AndroidDocumentSessionEnvironment @Inject constructor(
    private val dataRepository: DataRepository,
    private val getDocumentUseCase: GetDocumentUseCase,
    private val getCurrentBackendConfigUseCase: GetCurrentBackendConfigUseCase,
    private val authRepository: AuthRepository,
    private val preferencesHolder: KutePreferencesHolder,
    isOfflineModeEnabledFlowUseCase: IsOfflineModeEnabledFlowUseCase,
    private val localDocumentStore: LocalDocumentStore,
    private val localCopyRedirects: LocalCopyRedirects,
    networkManager: NetworkManager,
    private val syncedDocumentFactory: SyncedDocumentFactory,
    private val openDocuments: OpenDocuments,
) : DocumentSessionEnvironment {

    override val ioDispatcher = Dispatchers.IO

    override val offlineMode = isOfflineModeEnabledFlowUseCase()

    override val networkChanges = networkManager.defaultNetwork.newValues().map { }

    override val connectsAutomatically: Boolean
        get() = preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue.value

    override val sessionChanges = authRepository.sessionChanges

    override fun isSignedIn(backendId: Long) = authRepository.isSignedIn(backendId)

    override fun onUnauthenticated(backendId: Long) = authRepository.onUnauthenticated(backendId)

    override suspend fun currentBackend(): BackendConfig? = getCurrentBackendConfigUseCase()

    override fun createSyncedDocument(
        documentId: String,
        serverConfig: BackendServerConfig,
        backendId: Long,
        listener: AutomergeDocumentSyncManager.Listener,
    ): SyncedDocument = syncedDocumentFactory.create(documentId, serverConfig, backendId, listener)

    override fun onDocumentOpen(documentId: String?) = openDocuments.set(this, documentId)

    override fun hasLocalCopy(documentId: String) = localDocumentStore.contains(documentId)

    override suspend fun followRedirect(documentId: String) = localCopyRedirects.beforeOpening(documentId)

    override suspend fun followAllRedirects() {
        localCopyRedirects.followAll()
    }

    override suspend fun loadDocument(documentId: String, onCached: (CachedDocument?) -> Unit): DocumentLoad {
        val resource = getDocumentUseCase(documentId) { onCached(it?.toCachedDocument()) }
        return DocumentLoad(
            document = resource.data?.toCachedDocument(),
            error = (resource as? Resource.Error)?.error,
        )
    }

    override suspend fun updateContentCache(documentId: String, text: String) {
        dataRepository.updateDocumentContentInCache(documentId, text)
    }

    private fun DocumentEntity.toCachedDocument() = CachedDocument(name, content.target?.text)
}
