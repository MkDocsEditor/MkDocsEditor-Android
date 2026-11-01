package de.markusressel.mkdocseditor.service

import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.backendconfig.common.domain.GetCurrentBackendConfigUseCase
import de.markusressel.mkdocseditor.feature.editor.domain.SyncedDocumentFactory
import de.markusressel.mkdocsrestclient.sync.automerge.AutomergeDocumentSyncManager
import de.markusressel.mkdocsrestclient.sync.automerge.LocalCopyRedirects
import de.markusressel.mkdocsrestclient.sync.automerge.LocalDocumentStore
import de.markusressel.mkdocsrestclient.sync.automerge.UnsyncedUploadWatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Brings the unsynced changes of the local copies to the server, one document at a time.
 *
 * A copy only goes to the server it came from: ids derive from paths, so the same id on another
 * backend is another page. Old copies that do not know their server wait until they are opened.
 */
internal class UploadUnsyncedChanges(
    private val pending: () -> Set<String>,
    private val followMoves: suspend () -> Unit,
    private val currentOrigin: suspend () -> String?,
    private val originOf: (documentId: String) -> String?,
    private val upload: suspend (documentId: String) -> Outcome,
) {
    enum class Outcome { Done, Unreachable, SignedOut }

    enum class Result { Finished, TryAgainLater }

    @Inject
    constructor(
        store: LocalDocumentStore,
        pendingUploads: PendingUploads,
        redirects: LocalCopyRedirects,
        backend: GetCurrentBackendConfigUseCase,
        factory: SyncedDocumentFactory,
    ) : this(
        pending = pendingUploads::current,
        followMoves = {
            store.scanUnsynced()
            redirects.followAll()
        },
        currentOrigin = {
            backend()?.serverConfig?.let { AutomergeDocumentSyncManager.originOf(it.domain, it.port) }
        },
        originOf = { store.load(it)?.origin },
        upload = { documentId -> uploadOne(documentId, backend, factory) },
    )

    suspend operator fun invoke(): Result {
        followMoves()
        val origin = currentOrigin() ?: return Result.Finished
        // uploading takes a while, and another document may get changes meanwhile
        val attempted = mutableSetOf<String>()
        while (true) {
            val documentId = pending().firstOrNull { it !in attempted } ?: return Result.Finished
            attempted += documentId
            if (originOf(documentId) != origin) continue
            when (upload(documentId)) {
                Outcome.Done -> Unit
                Outcome.SignedOut -> return Result.Finished
                // the next documents would only wait for the same server
                Outcome.Unreachable -> return Result.TryAgainLater
            }
        }
    }

    private companion object {
        const val UPLOAD_TIMEOUT_MS = 30_000L

        suspend fun uploadOne(
            documentId: String,
            backend: GetCurrentBackendConfigUseCase,
            factory: SyncedDocumentFactory,
        ): Outcome {
            val config = backend() ?: return Outcome.SignedOut
            val serverConfig = config.serverConfig ?: return Outcome.SignedOut
            val watcher = UnsyncedUploadWatcher()
            val document = factory.create(documentId, serverConfig, config.id, watcher)
            try {
                if (!document.hasUnsyncedChanges) return Outcome.Done
                withContext(Dispatchers.Main) { document.connect() }
                val result = withTimeoutOrNull(UPLOAD_TIMEOUT_MS) { watcher.await() }
                Timber.d { "Upload of the changes of $documentId: ${result ?: "no answer"}" }
                return when (result) {
                    null -> Outcome.Unreachable
                    UnsyncedUploadWatcher.Result.SignedOut -> Outcome.SignedOut
                    UnsyncedUploadWatcher.Result.Uploaded, UnsyncedUploadWatcher.Result.Refused -> Outcome.Done
                }
            } finally {
                // writes the copy with what the server confirmed
                withContext(NonCancellable + Dispatchers.Main) { document.shutdown() }
            }
        }
    }
}
