package de.markusressel.mkdocseditor.auth.di

import android.content.Context
import android.os.Build
import android.provider.Settings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.auth.api.AuthApiFactory
import de.markusressel.mkdocseditor.auth.api.OkHttpAuthApi
import de.markusressel.mkdocseditor.auth.domain.AuthRepository
import de.markusressel.mkdocseditor.auth.token.AesGcmTokenCipher
import de.markusressel.mkdocseditor.auth.token.AndroidKeystoreKey
import de.markusressel.mkdocseditor.auth.token.EncryptedTokenStore
import de.markusressel.mkdocseditor.auth.token.SharedPreferencesKeyValueStore
import de.markusressel.mkdocseditor.auth.token.TokenStore
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
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

    @Provides
    @Singleton
    fun provideAuthApiFactory(): AuthApiFactory {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // Argon2id hashing on the server may queue for a moment
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        return AuthApiFactory { baseUrl -> OkHttpAuthApi(baseUrl, client) }
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        tokenStore: TokenStore,
        apiFactory: AuthApiFactory,
    ): AuthRepository = AuthRepository(
        tokenStore = tokenStore,
        apiFactory = apiFactory,
        deviceName = deviceName(context),
    )

    /**
     * The name the user gave the device, else manufacturer and model, like "Google Pixel 8"
     */
    private fun deviceName(context: Context): String {
        val userName = try {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } catch (_: Exception) {
            null
        }
        val model = if (Build.MODEL.startsWith(Build.MANUFACTURER, ignoreCase = true)) {
            Build.MODEL
        } else {
            "${Build.MANUFACTURER.replaceFirstChar(Char::uppercase)} ${Build.MODEL}"
        }
        return (userName?.takeIf { it.isNotBlank() } ?: model).take(64)
    }
}
