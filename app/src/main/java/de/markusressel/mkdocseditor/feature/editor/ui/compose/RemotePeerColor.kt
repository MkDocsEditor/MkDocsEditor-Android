package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.contrastRatio
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.wcagLuminance
import de.markusressel.mkdocsrestclient.sync.automerge.RemotePeer
import kotlin.math.absoluteValue

/**
 * Colors used for peers that don't send a (valid) color of their own.
 * Mid tones, so they are visible on the light and on the dark editor background.
 */
internal val fallbackPeerColors = listOf(
    Color(0xFFE5534B),
    Color(0xFFB467D1),
    Color(0xFF3D8FE0),
    Color(0xFF14A08F),
    Color(0xFF5E9A2E),
    Color(0xFFC97716),
    Color(0xFFC4708A),
    Color(0xFF6F7FE0),
)

/**
 * The minimum contrast of a cursor or selection color against the editor background,
 * the WCAG AA value for graphical objects
 */
private const val MIN_PEER_COLOR_CONTRAST = 3.0

/**
 * This color, lightened on a dark background or darkened on a light one until it has a
 * contrast of at least 3:1, so the cursor of every user is visible in both themes.
 */
internal fun Color.readableOn(background: Color): Color {
    val opaque = copy(alpha = 1f)
    if (contrastRatio(opaque, background) >= MIN_PEER_COLOR_CONTRAST) return opaque
    val target = if (background.wcagLuminance() < 0.18) Color.White else Color.Black
    var fraction = 0f
    var result = opaque
    while (fraction < 1f && contrastRatio(result, background) < MIN_PEER_COLOR_CONTRAST) {
        fraction += 0.05f
        result = lerp(opaque, target, fraction.coerceAtMost(1f))
    }
    return result
}

/**
 * Black or white, whichever is easier to read on the label background [color]
 */
internal fun labelTextColorOn(color: Color): Color =
    if (contrastRatio(Color.Black, color) >= contrastRatio(Color.White, color)) Color.Black else Color.White

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
