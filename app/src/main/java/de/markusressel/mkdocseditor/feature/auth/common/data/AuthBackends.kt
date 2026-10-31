package de.markusressel.mkdocseditor.feature.auth.common.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.LoginHintStore
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.toBackendConfig
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the auth screens need to know about the configured backends
 */
interface AuthBackends {
    suspend fun get(id: Long): BackendConfig?

    suspend fun all(): List<BackendConfig>

    suspend fun selected(): BackendConfig?

    /**
     * Creates and selects a backend config for the given API URL, for a link opened on a fresh install
     */
    suspend fun create(url: ParsedBackendUrl): BackendConfig

    suspend fun select(config: BackendConfig)

    fun usernameHint(id: Long): String?

    fun setUsernameHint(id: Long, username: String)
}

/**
 * A backend URL typed by the user, like `https://backend.wiki.example.com` or `http://192.168.2.20:7413`
 */
data class ParsedBackendUrl(
    val host: String,
    val port: Int,
    val useSsl: Boolean,
) {
    companion object {
        fun parse(text: String): ParsedBackendUrl? {
            val input = text.trim().let { if ("://" in it) it else "https://$it" }
            val uri = try {
                URI(input)
            } catch (_: Exception) {
                return null
            }
            val ssl = when (uri.scheme?.lowercase()) {
                "https" -> true
                "http" -> false
                else -> return null
            }
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val path = uri.rawPath.orEmpty().trim('/')
            val port = if (uri.port != -1) uri.port else if (ssl) 443 else 80
            return ParsedBackendUrl(
                host = if (path.isEmpty()) host else "$host/$path",
                port = port,
                useSsl = ssl,
            )
        }
    }
}

@Singleton
internal class DefaultAuthBackends @Inject constructor(
    private val repository: BackendConfigRepository,
    private val loginHintStore: LoginHintStore,
) : AuthBackends {

    override suspend fun get(id: Long): BackendConfig? = repository.getBackendConfig(id)?.toBackendConfig()

    override suspend fun all(): List<BackendConfig> = repository.getBackendConfigs().map { it.toBackendConfig() }

    override suspend fun selected(): BackendConfig? = repository.selectedBackendConfig()?.toBackendConfig()

    override suspend fun create(url: ParsedBackendUrl): BackendConfig {
        val names = all().map { it.name }.toSet()
        val baseName = url.host.substringBefore('/')
        val name = generateSequence(1) { it + 1 }
            .map { if (it == 1) baseName else "$baseName ($it)" }
            .first { it !in names }
        val config = BackendConfig(
            name = name,
            description = "",
            serverConfig = BackendServerConfig(domain = url.host, port = url.port, useSsl = url.useSsl),
            mkDocsWebConfig = null,
            mkDocsWebAuthConfig = null,
            isSelected = false,
        )
        val id = repository.addOrUpdate(config)
        val created = requireNotNull(get(id))
        repository.selectBackendConfig(created)
        return created
    }

    override suspend fun select(config: BackendConfig) = repository.selectBackendConfig(config)

    override fun usernameHint(id: Long): String? = loginHintStore.username(id)

    override fun setUsernameHint(id: Long, username: String) = loginHintStore.setUsername(id, username)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AuthBackendsModule {
    @Binds
    abstract fun bindAuthBackends(impl: DefaultAuthBackends): AuthBackends
}
