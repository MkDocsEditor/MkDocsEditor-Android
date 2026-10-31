package de.markusressel.mkdocseditor.auth.discovery

import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonEncodingException
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import de.markusressel.mkdocseditor.auth.api.AuthApiException
import de.markusressel.mkdocseditor.auth.api.AuthApiFactory
import de.markusressel.mkdocseditor.auth.api.ServerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI

/**
 * A MkDocsEditor instance found from an address the user typed
 *
 * @param backendUrl the API base URL, with a trailing slash
 * @param webClientUrl the web client, when known
 */
data class DiscoveredServer(
    val backendUrl: String,
    val info: ServerInfo,
    val wikiUrl: String?,
    val webClientUrl: String?,
) {
    /** a name for the server list: the instance name, else the host */
    val displayName: String
        get() = info.name?.takeIf { it.isNotBlank() } ?: URI(backendUrl).host
}

sealed interface DiscoveryResult {
    data class Found(val server: DiscoveredServer) : DiscoveryResult

    /** not a URL or host name */
    data object InvalidAddress : DiscoveryResult

    /** nothing answered at that address */
    data class Unreachable(val cause: IOException) : DiscoveryResult

    /** something answered, but neither a MkDocsEditor web client nor a backend */
    data object NotMkDocsEditor : DiscoveryResult
}

/**
 * Finds a MkDocsEditor instance from an address the user typed
 */
fun interface ServerDiscoverer {
    suspend fun discover(input: String): DiscoveryResult
}

/**
 * Finds the backend behind an address: the web client's `config.json` (it names the backend
 * and the wiki), else the backend's `server-info/` at the address itself
 */
class ServerDiscovery(
    private val client: OkHttpClient,
    private val apiFactory: AuthApiFactory,
) : ServerDiscoverer {

    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    override suspend fun discover(input: String): DiscoveryResult {
        val base = normalizeAddress(input) ?: return DiscoveryResult.InvalidAddress
        var networkError: IOException? = null
        // a deep link into the web client (a single page app) has its config.json at the root
        val root = URI(base).resolve("/").toString()
        for (candidate in listOf(base, root).distinct()) {
            try {
                findAt(candidate)?.let { return DiscoveryResult.Found(it) }
            } catch (e: IOException) {
                networkError = networkError ?: e
            }
        }
        return networkError?.let { DiscoveryResult.Unreachable(it) } ?: DiscoveryResult.NotMkDocsEditor
    }

    /**
     * 1. a web client: its config.json names the backend; 2. the backend itself
     */
    private suspend fun findAt(base: String): DiscoveredServer? {
        var networkError: IOException? = null
        try {
            val config = webClientConfig(base)
            val backendUrl = config?.get("backendUrl")?.let { resolve(base, it) }
            if (backendUrl != null) {
                serverInfo(backendUrl)?.let { info ->
                    return DiscoveredServer(
                        backendUrl = backendUrl,
                        info = info,
                        wikiUrl = config["wikiUrl"]?.takeIf { it.isNotBlank() } ?: info.wikiUrl,
                        webClientUrl = base,
                    )
                }
            }
        } catch (e: IOException) {
            networkError = e
        }
        try {
            serverInfo(base)?.let { info ->
                return DiscoveredServer(backendUrl = base, info = info, wikiUrl = info.wikiUrl, webClientUrl = info.webClientUrl)
            }
        } catch (e: IOException) {
            networkError = networkError ?: e
        }
        networkError?.let { throw it }
        return null
    }

    /**
     * @return backendUrl and wikiUrl of `<base>/config.json`, null when there is none
     */
    private suspend fun webClientConfig(base: String): Map<String, String>? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(base + "config.json").header("Accept", "application/json").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val map = try {
                mapAdapter.fromJson(response.body.string())
            } catch (_: Exception) {
                null
            } ?: return@withContext null
            map.mapNotNull { (key, value) -> (value as? String)?.let { key to it.trim() } }.toMap()
        }
    }

    /**
     * @return the server info, null when [backendUrl] is no MkDocsEditor backend
     */
    private suspend fun serverInfo(backendUrl: String): ServerInfo? {
        val api = apiFactory.create(backendUrl)
        return try {
            api.serverInfo()
        } catch (e: AuthApiException) {
            if (e.status != 404) return null
            // a backend from before /server-info/: /auth/status/ still proves it is one
            try {
                api.status().let { ServerInfo(setupRequired = it.setupRequired, authDisabled = it.authDisabled, passkeys = it.passkeys) }
            } catch (_: AuthApiException) {
                null
            } catch (_: JsonDataException) {
                null
            } catch (_: JsonEncodingException) {
                null
            }
        } catch (_: JsonDataException) {
            null
        } catch (_: JsonEncodingException) {
            null
        }
    }

    companion object {
        /**
         * `wiki.example.com` -> `https://wiki.example.com/`; keeps http, ports and paths, drops
         * query and fragment
         */
        fun normalizeAddress(input: String): String? {
            val text = input.trim().takeIf { it.isNotEmpty() && ' ' !in it } ?: return null
            val withScheme = if ("://" in text) text else "https://$text"
            val uri = try {
                URI(withScheme)
            } catch (_: Exception) {
                return null
            }
            if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            // a pasted page of the web client, like …/index.html, counts as its folder
            val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
                .let { if (it.lastOrNull()?.contains('.') == true) it.dropLast(1) else it }
            val dir = segments.joinToString("") { "$it/" }
            return "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port/$dir"
        }

        /** a backendUrl of config.json, absolute or relative to the web client */
        internal fun resolve(base: String, url: String): String? {
            if (url.isBlank()) return null
            val resolved = try {
                URI(base).resolve(url.trim())
            } catch (_: Exception) {
                return null
            }
            if (resolved.scheme?.lowercase() !in setOf("http", "https") || resolved.host.isNullOrBlank()) return null
            return resolved.toString().trimEnd('/') + "/"
        }
    }
}
