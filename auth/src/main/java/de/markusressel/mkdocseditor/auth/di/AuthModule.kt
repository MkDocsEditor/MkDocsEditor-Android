package de.markusressel.mkdocseditor.auth.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.auth.token.AesGcmTokenCipher
import de.markusressel.mkdocseditor.auth.token.AndroidKeystoreKey
import de.markusressel.mkdocseditor.auth.token.EncryptedTokenStore
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import de.markusressel.mkdocseditor.auth.token.TokenStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    /**
     * Name of the preferences file with the encrypted sessions. It is excluded from backups,
     * because the Keystore key that decrypts it never leaves the device.
     */
    const val SESSION_PREFERENCES_NAME = "auth_sessions"

    private const val KEY_ALIAS = "mkdocseditor_auth_sessions"

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        return EncryptedTokenStore(
            storage = SharedPreferencesKeyValueStore(
                context.getSharedPreferences(SESSION_PREFERENCES_NAME, Context.MODE_PRIVATE)
            ),
            cipher = AesGcmTokenCipher { AndroidKeystoreKey.getOrCreate(KEY_ALIAS) },
        )
    }
}
