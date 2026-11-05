package de.markusressel.mkdocseditor.feature.editor.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.auth.token.KeyValueStore
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import de.markusressel.mkdocsrestclient.sync.automerge.newInstanceId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The id of this app installation as a client instance, the same in every editor and after a restart. Leaving a document
 * and coming back is then the same instance returning, which ends its ghost instead of adding a second one.
 */
@Singleton
internal class ClientInstanceId internal constructor(private val storage: KeyValueStore) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        SharedPreferencesKeyValueStore(context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE))
    )

    val value: String by lazy {
        storage.get(KEY_ID) ?: newInstanceId().also { storage.put(KEY_ID, it) }
    }

    private companion object {
        const val PREFERENCES_NAME = "client_instance"
        const val KEY_ID = "id"
    }
}
