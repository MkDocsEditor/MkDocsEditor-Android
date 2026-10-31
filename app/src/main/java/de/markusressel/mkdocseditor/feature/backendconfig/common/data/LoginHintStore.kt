package de.markusressel.mkdocseditor.feature.backendconfig.common.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers the last username per backend, to prefill the login. Not a secret.
 */
@Singleton
internal class LoginHintStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("login_hints", Context.MODE_PRIVATE)

    fun username(backendId: Long): String? = preferences.getString(key(backendId), null)

    fun setUsername(backendId: Long, username: String) = preferences.edit {
        putString(key(backendId), username)
    }

    private fun key(backendId: Long) = "username_$backendId"
}
