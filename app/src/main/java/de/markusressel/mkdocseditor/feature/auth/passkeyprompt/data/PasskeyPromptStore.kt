package de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data

import android.content.Context
import android.content.SharedPreferences
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Remembers per backend whether the passkey suggestion was skipped and which passkeys were
 * created on this device. Nothing secret: ids and times only.
 */
interface PasskeyPromptStore {
    fun state(backendId: Long): PasskeyPromptState
    fun skipped(backendId: Long, at: Instant)
    fun reminderDismissed(backendId: Long, at: Instant)

    /** a passkey was created on this device: no more suggestions */
    fun registered(backendId: Long, passkeyId: String)
}

@Singleton
internal class SharedPreferencesPasskeyPromptStore @Inject constructor(
    @ApplicationContext context: Context,
) : PasskeyPromptStore {

    private val prefs: SharedPreferences = context.getSharedPreferences("passkey_prompt", Context.MODE_PRIVATE)

    override fun state(backendId: Long) = PasskeyPromptState(
        skippedAt = prefs.getLong("$backendId.skipped", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        reminderDismissedAt = prefs.getLong("$backendId.dismissed", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
        registeredHere = prefs.getStringSet("$backendId.registered", emptySet()).orEmpty().toSet(),
    )

    override fun skipped(backendId: Long, at: Instant) {
        prefs.edit().putLong("$backendId.skipped", at.toEpochMilli()).apply()
    }

    override fun reminderDismissed(backendId: Long, at: Instant) {
        prefs.edit().putLong("$backendId.dismissed", at.toEpochMilli()).apply()
    }

    override fun registered(backendId: Long, passkeyId: String) {
        val ids = state(backendId).registeredHere + passkeyId
        prefs.edit()
            .putStringSet("$backendId.registered", ids)
            .remove("$backendId.skipped")
            .remove("$backendId.dismissed")
            .apply()
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PasskeyPromptModule {
    @Binds
    abstract fun bindPasskeyPromptStore(impl: SharedPreferencesPasskeyPromptStore): PasskeyPromptStore

    @Binds
    abstract fun bindCredentialProviders(impl: AndroidCredentialProviders): CredentialProviders
}
