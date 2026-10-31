package de.markusressel.mkdocseditor.auth.link

import java.net.URI

/**
 * Kind of a setup, invite or reset link, see docs/auth.md section 7
 */
enum class LinkKind(val pathSegment: String) {
    Setup("setup"),
    Invite("invite"),
    Reset("reset");

    companion object {
        fun fromPathSegment(segment: String): LinkKind? = entries.firstOrNull { it.pathSegment == segment.lowercase() }

        /**
         * Maps the `kind` field of a link inspection
         */
        fun fromApi(kind: String): LinkKind? = fromPathSegment(kind)
    }
}

/**
 * A link token, with the kind the URL says (the server has the final word on the kind)
 */
data class AuthLink(
    val token: String,
    val kind: LinkKind?,
) {
    override fun toString(): String = "AuthLink(kind=$kind, token=***)"
}

object AuthLinkParser {

    /** custom scheme fallback: `mkdocseditor://invite#mdl_…` */
    const val CUSTOM_SCHEME = "mkdocseditor"

    private const val TOKEN_PREFIX = "mdl_"
    private val tokenPattern = Regex("^mdl_[A-Za-z0-9_-]{20,128}$")

    /**
     * Parses a link the user opened or pasted:
     *
     * - `https://edit.wiki.example.com/invite#mdl_…` (any host, any path prefix)
     * - `mkdocseditor://invite#mdl_…`
     * - a bare `mdl_…` token
     *
     * @return the link, or null if the text holds no link token
     */
    fun parse(text: String): AuthLink? {
        val input = text.trim()
        if (input.isEmpty()) return null

        if (input.startsWith(TOKEN_PREFIX)) {
            return input.takeIf(::isValidToken)?.let { AuthLink(it, null) }
        }

        val uri = try {
            URI(input)
        } catch (_: Exception) {
            return null
        }
        val token = uri.rawFragment?.trim()?.takeIf(::isValidToken) ?: return null

        val kind = when (uri.scheme?.lowercase()) {
            "https", "http" -> uri.path.orEmpty().trimEnd('/').substringAfterLast('/')
                .let(LinkKind::fromPathSegment)

            CUSTOM_SCHEME -> (uri.host ?: uri.schemeSpecificPart.trim('/').substringBefore('/'))
                .let(LinkKind::fromPathSegment)

            else -> return null
        }
        return AuthLink(token, kind)
    }

    fun isValidToken(token: String): Boolean = tokenPattern.matches(token)
}
