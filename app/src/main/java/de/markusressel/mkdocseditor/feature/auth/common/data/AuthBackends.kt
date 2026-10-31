package de.markusressel.mkdocseditor.feature.auth.common.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendConfigRepository
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.AuthConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.BackendServerConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.MkDocsWebConfig
import de.markusressel.mkdocseditor.feature.backendconfig.common.data.baseUrl
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
     * Creates (or reuses) and selects the backend config for the given API URL, for a link opened on a fresh install
     */
    suspend fun create(url: ParsedBackendUrl): BackendConfig

    suspend fun select(config: BackendConfig)

    /**
     * Finds the backend config for [setup]'s API URL (fills in what it lacks) or creates one,
     * and selects it. Used by the connect link and the "Server address" onboarding.
     */
    suspend fun connect(setup: ServerSetup): BackendConfig

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
    /** back to a URL, like `https://example.com:443/api/` */
    fun toUrl(): String {
        val protocol = if (useSsl) "https" else "http"
        val path = host.substringAfter('/', "").trim('/')
        return "$protocol://${host.substringBefore('/')}:$port/" + if (path.isEmpty()) "" else "$path/"
    }

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

/**
 * A server to add, mostly from discovery
 *
 * @param backendUrl the API base URL
 * @param name the user's name for it, else [suggestedName]
 * @param suggestedName the instance name from /server-info/
 */
data class ServerSetup(
    val backendUrl: String,
    val suggestedName: String? = null,
    val name: String? = null,
    val description: String? = null,
    val wikiUrl: String? = null,
    val wikiAuth: AuthConfig? = null,
) {
    override fun toString() = "ServerSetup(backendUrl=$backendUrl, name=${name ?: suggestedName}, wikiUrl=$wikiUrl)"
}

/**
 * The config change of [AuthBackends.connect], without persistence
 */
internal object BackendConfigPlanner {

    /**
     * @return the config to save: an existing one with the same API URL (keeping what the
     * user set) or a new one; null when the backend URL cannot be used
     */
    fun plan(existing: List<BackendConfig>, setup: ServerSetup): BackendConfig? {
        val url = ParsedBackendUrl.parse(setup.backendUrl) ?: return null
        val server = BackendServerConfig(domain = url.host, port = url.port, useSsl = url.useSsl)
        val wiki = setup.wikiUrl?.let(ParsedBackendUrl::parse)?.let { MkDocsWebConfig(domain = it.host, port = it.port, useSsl = it.useSsl) }

        val match = existing.firstOrNull { it.serverConfig?.baseUrl == server.baseUrl }
        if (match != null) {
            return match.copy(
                name = setup.name?.takeIf { it.isNotBlank() } ?: match.name,
                description = setup.description?.takeIf { it.isNotBlank() } ?: match.description,
                mkDocsWebConfig = match.mkDocsWebConfig ?: wiki,
                mkDocsWebAuthConfig = setup.wikiAuth ?: match.mkDocsWebAuthConfig,
            )
        }

        val names = existing.map { it.name }.toSet()
        val baseName = setup.name?.takeIf { it.isNotBlank() }?.trim()
            ?: setup.suggestedName?.takeIf { it.isNotBlank() }?.trim()
            ?: url.host.substringBefore('/')
        val name = generateSequence(1) { it + 1 }
            .map { if (it == 1) baseName else "$baseName ($it)" }
            .first { it !in names }
        return BackendConfig(
            name = name,
            description = setup.description?.trim().orEmpty(),
            serverConfig = server,
            mkDocsWebConfig = wiki,
            mkDocsWebAuthConfig = setup.wikiAuth,
            isSelected = false,
        )
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

    override suspend fun create(url: ParsedBackendUrl): BackendConfig = connect(ServerSetup(backendUrl = url.toUrl()))

    override suspend fun connect(setup: ServerSetup): BackendConfig {
        val config = requireNotNull(BackendConfigPlanner.plan(all(), setup)) { "unusable backend url" }
        val id = repository.addOrUpdate(config)
        val saved = requireNotNull(get(id))
        repository.selectBackendConfig(saved)
        return saved
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
