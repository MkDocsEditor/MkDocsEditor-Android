package de.markusressel.mkdocseditor.auth.link

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime
import java.util.Base64

/**
 * The payload of a connect link (device pairing, docs/auth.md "Device pairing"): the web
 * client shows `{clientUrl}/connect#<payload>` as a QR code for the app
 *
 * @param backendUrl the API base URL, with a trailing slash
 */
data class ConnectPayload(
    val backendUrl: String,
    val username: String,
    val token: String,
    val expiresAt: Instant?,
) {
    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt != null && !now.isBefore(expiresAt)

    override fun toString() = "ConnectPayload(backendUrl=$backendUrl, username=$username, expiresAt=$expiresAt, token=***)"
}

sealed interface ConnectLinkResult {
    data class Valid(val payload: ConnectPayload) : ConnectLinkResult

    /** a payload version this app does not know: the app needs an update */
    data class UnsupportedVersion(val version: Int?) : ConnectLinkResult

    /** a connect link, but the payload cannot be read */
    data object Invalid : ConnectLinkResult
}

object ConnectLinkParser {

    const val PATH_SEGMENT = "connect"
    const val SUPPORTED_VERSION = 1

    private val tokenPattern = Regex("^mdd_[A-Za-z0-9_-]{43}$")
    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    /**
     * True for `https://<host>/…/connect#…` and `mkdocseditor://connect#…`, whatever the payload
     */
    fun isConnectLink(text: String): Boolean = fragmentOf(text) != null

    /**
     * Parses a connect link the user opened, scanned or pasted
     *
     * @return null when the text is no connect link at all
     */
    fun parse(text: String): ConnectLinkResult? {
        val fragment = fragmentOf(text) ?: return null
        return decode(fragment)
    }

    /**
     * Decodes the fragment: base64url (no padding) of a UTF-8 JSON object
     */
    fun decode(payload: String): ConnectLinkResult {
        val json = try {
            String(Base64.getUrlDecoder().decode(payload.trim().trimEnd('=')), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            return ConnectLinkResult.Invalid
        }
        val map = try {
            mapAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return ConnectLinkResult.Invalid

        val version = (map["v"] as? Number)?.toInt()
        if (version != SUPPORTED_VERSION) return ConnectLinkResult.UnsupportedVersion(version)

        val backendUrl = (map["backendUrl"] as? String)?.let(::normalizeBackendUrl) ?: return ConnectLinkResult.Invalid
        val username = (map["username"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return ConnectLinkResult.Invalid
        val token = (map["token"] as? String)?.takeIf(tokenPattern::matches) ?: return ConnectLinkResult.Invalid
        val expiresAt = (map["expiresAt"] as? String)?.let {
            try {
                OffsetDateTime.parse(it).toInstant()
            } catch (_: Exception) {
                null
            }
        }
        return ConnectLinkResult.Valid(ConnectPayload(backendUrl, username, token, expiresAt))
    }

    private fun fragmentOf(text: String): String? {
        val uri = try {
            URI(text.trim())
        } catch (_: Exception) {
            return null
        }
        val segment = when (uri.scheme?.lowercase()) {
            "https", "http" -> uri.path.orEmpty().trimEnd('/').substringAfterLast('/')
            AuthLinkParser.CUSTOM_SCHEME -> uri.host ?: uri.schemeSpecificPart.trim('/').substringBefore('/')
            else -> return null
        }
        if (!segment.equals(PATH_SEGMENT, ignoreCase = true)) return null
        return uri.rawFragment?.takeIf { it.isNotBlank() }
    }

    /**
     * @return the URL with a trailing slash, or null if it is no http(s) URL
     */
    internal fun normalizeBackendUrl(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank()) return null
        return uri.toString().trimEnd('/') + "/"
    }
}
