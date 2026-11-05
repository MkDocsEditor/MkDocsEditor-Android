package de.markusressel.mkdocseditor.feature.join.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** The document a join link points at, on a server of this app */
data class JoinTarget(
    val backendId: Long,
    val documentId: String,
    val from: String?,
)

/**
 * The document to open once the person who followed a join link is signed in. Signing in or waiting for an invite can
 * outlast the process, so it is stored. It holds nothing secret.
 */
@Singleton
class PendingJoin internal constructor(
    private val storage: KeyValueStore,
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesKeyValueStore(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
    )

    private val _open = MutableStateFlow<JoinTarget?>(null)

    /** The document to open now. A state and not an event: the file browser may not be in composition yet. */
    val open: StateFlow<JoinTarget?> = _open.asStateFlow()

    /** remembers [target] until the user is signed in to its server, see [release] */
    fun remember(target: JoinTarget) {
        storage.put(KEY_BACKEND, target.backendId.toString())
        storage.put(KEY_DOCUMENT, target.documentId)
        if (target.from != null) storage.put(KEY_FROM, target.from) else storage.remove(KEY_FROM)
    }

    /** the user is signed in to [backendId]: a document remembered for it opens now, one of another server keeps waiting */
    fun release(backendId: Long) {
        val documentId = storage.get(KEY_DOCUMENT) ?: return
        if (storage.get(KEY_BACKEND)?.toLongOrNull() != backendId) return
        val from = storage.get(KEY_FROM)
        forget()
        _open.value = JoinTarget(backendId, documentId, from)
    }

    /** the file browser has opened the document */
    fun opened() {
        _open.value = null
    }

    fun forget() {
        storage.remove(KEY_BACKEND)
        storage.remove(KEY_DOCUMENT)
        storage.remove(KEY_FROM)
    }

    private companion object {
        const val PREFERENCES_NAME = "pending_join"
        const val KEY_BACKEND = "backend"
        const val KEY_DOCUMENT = "document"
        const val KEY_FROM = "from"
    }
}
