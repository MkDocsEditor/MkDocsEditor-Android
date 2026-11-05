package de.markusressel.mkdocseditor.auth.link

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * An invitation into a document, sent through a messenger: `{clientUrl}/join/{documentId}?from={accountId}`.
 * It holds no secret and grants nothing, access still needs an account. The app and the web client use the same format.
 *
 * @param clientUrl the web client this link points at, with a trailing slash: it names the server
 * @param from the account of the sender, to find where they are when the link is opened
 */
data class JoinLink(
    val clientUrl: String,
    val documentId: String,
    val from: String?,
)

object JoinLinkParser {

    private const val PATH_SEGMENT = "join"
    private const val FROM = "from"

    /** @return the link for [documentId] on the web client at [clientUrl], with or without a trailing slash */
    fun build(clientUrl: String, documentId: String, from: String?): String {
        val query = from?.let { "?$FROM=${encode(it)}" }.orEmpty()
        return "${clientUrl.trimEnd('/')}/$PATH_SEGMENT/${encode(documentId)}$query"
    }

    /** @return the link, or null if [text] is no https link to a document, so nothing else can reach the join screen */
    fun parse(text: String): JoinLink? {
        val uri = try {
            URI(text.trim())
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme?.lowercase() !in setOf("https", "http") || uri.host.isNullOrBlank()) return null

        val segments = uri.rawPath.orEmpty().split('/').filter { it.isNotEmpty() }
        if (segments.size < 2 || !segments[segments.size - 2].equals(PATH_SEGMENT, ignoreCase = true)) return null
        val documentId = decode(segments.last())?.takeIf { it.isNotBlank() } ?: return null

        val port = if (uri.port != -1) ":${uri.port}" else ""
        val prefix = segments.dropLast(2).joinToString("") { "$it/" }
        return JoinLink(
            clientUrl = "${uri.scheme.lowercase()}://${uri.host.lowercase()}$port/$prefix",
            documentId = documentId,
            from = uri.rawQuery?.split('&')
                ?.firstOrNull { it.startsWith("$FROM=") }
                ?.let { decode(it.removePrefix("$FROM=")) }
                ?.takeIf { it.isNotBlank() },
        )
    }

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")

    private fun decode(value: String): String? = try {
        // a plus is a plain character in a path
        URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        null
    }
}
