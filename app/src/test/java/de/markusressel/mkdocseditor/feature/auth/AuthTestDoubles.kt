package de.markusressel.mkdocseditor.feature.auth

import android.content.Context
import de.markusressel.mkdocseditor.feature.auth.common.data.AuthBackends
import de.markusressel.mkdocseditor.feature.auth.common.data.CredentialClient
import de.markusressel.mkdocseditor.feature.auth.common.data.ParsedBackendUrl
import de.markusressel.mkdocseditor.feature.auth.common.data.PickedCredential
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig

fun backendConfig(id: Long, name: String = "Wiki", domain: String = "backend.example.com") = BackendConfig(
    id = id,
    name = name,
    description = "",
    serverConfig = BackendServerConfig(domain = domain, port = 443, useSsl = true),
    mkDocsWebConfig = null,
    mkDocsWebAuthConfig = null,
    isSelected = false,
)

class FakeAuthBackends(vararg initial: BackendConfig) : AuthBackends {
    val configs = initial.toMutableList()
    val hints = mutableMapOf<Long, String>()
    var selectedId: Long? = null
    val created = mutableListOf<ParsedBackendUrl>()

    override suspend fun get(id: Long) = configs.firstOrNull { it.id == id }
    override suspend fun all() = configs.toList()
    override suspend fun selected() = configs.firstOrNull { it.id == selectedId }
    override suspend fun create(url: ParsedBackendUrl): BackendConfig {
        created += url
        val config = backendConfig(id = 100L + created.size, name = url.host, domain = url.host)
        configs += config
        selectedId = config.id
        return config
    }

    override suspend fun select(config: BackendConfig) {
        selectedId = config.id
    }

    override fun usernameHint(id: Long) = hints[id]
    override fun setUsernameHint(id: Long, username: String) {
        hints[id] = username
    }
}

/**
 * The UI calls the credential client, not the view models, so this one is never used by them
 */
object UnusedCredentialClient : CredentialClient {
    override suspend fun pickCredential(activityContext: Context, passkeyRequestJson: String?, includePasswords: Boolean): PickedCredential? =
        error("not used")

    override suspend fun createPasskey(activityContext: Context, requestJson: String): String? = error("not used")
    override suspend fun savePassword(activityContext: Context, username: String, password: String) = error("not used")
}
