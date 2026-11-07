package de.markusressel.mkdocseditor.feature.qr.domain

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The URL on a QR code sticker: `https://<web client>/q/<key>` (`docs/qr.md` of the backend). The web client and the
 * app open the same URL.
 */
internal object QrLink {

    private const val PATH_SEGMENT = "q"

    /** @param clientUrl the web client, with or without a trailing slash */
    fun build(clientUrl: String, key: String): String =
        "${clientUrl.trimEnd('/')}/$PATH_SEGMENT/${URLEncoder.encode(key, Charsets.UTF_8).replace("+", "%20")}"

    /** @return the key of a sticker URL as written, null if [text] is none */
    fun parse(text: String): String? {
        val uri = try {
            URI(text.trim())
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank()) return null
        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size < 2 || segments[segments.size - 2] != PATH_SEGMENT) return null
        return try {
            URLDecoder.decode(segments.last().replace("+", "%2B"), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }
}
