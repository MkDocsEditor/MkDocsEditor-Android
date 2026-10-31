package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlin.math.absoluteValue

/**
 * Colors used for peers that don't send a (valid) color of their own
 */
private val fallbackPeerColors = listOf(
    Color(0xFFE53935),
    Color(0xFF8E24AA),
    Color(0xFF1E88E5),
    Color(0xFF00897B),
    Color(0xFF7CB342),
    Color(0xFFFB8C00),
    Color(0xFF6D4C41),
    Color(0xFF3949AB),
)

/**
 * The color of this peer, parsed from its "#RRGGBB" or "#AARRGGBB" color string.
 * Falls back to a color derived from the username, so a user keeps the same color.
 */
internal val RemotePeer.composeColor: Color
    get() = parsePeerColor(color) ?: fallbackPeerColors[username.hashCode().absoluteValue % fallbackPeerColors.size]

/**
 * The name to show for this peer
 */
internal val RemotePeer.name: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: username

/**
 * Parses a "#RRGGBB" or "#AARRGGBB" color string
 *
 * @return the color, or null if the string is not a valid color
 */
internal fun parsePeerColor(color: String?): Color? {
    val hex = color?.trim()?.removePrefix("#") ?: return null
    if (hex.any { Character.digit(it, 16) < 0 }) {
        return null
    }
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> null
    }
}
