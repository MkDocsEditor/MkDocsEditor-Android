package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.contrastRatio
import de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight.wcagLuminance
import de.markusressel.mkdocsrestclient.sync.automerge.RemoteGhost
import de.markusressel.mkdocseditor.feature.avatar.data.Avatar
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

/** The same color for the same name. As a Long, because the absolute value of the smallest Int stays negative. */
internal fun fallbackPeerColor(username: String): Color =
    fallbackPeerColors[(username.hashCode().toLong().absoluteValue % fallbackPeerColors.size).toInt()]

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
 * The color of this peer, parsed from its "#RRGGBB" or "#AARRGGBB" color string: the one its instance chose, else the one
 * of its account. Falls back to a color derived from the username, so a user keeps the same color.
 */
internal val RemotePeer.composeColor: Color
    get() = parsePeerColor(instanceColor) ?: parsePeerColor(color) ?: fallbackPeerColor(username)

/**
 * The name to show for this peer
 */
/** the profile picture, null without one */
internal val RemotePeer.avatarModel: Avatar?
    get() = Avatar.of(accountId, avatar)

internal val RemotePeer.name: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: username

/** The name with the device, which tells apart several instances of one person */
internal val RemotePeer.label: String
    get() = listOfNotNull(name, deviceName?.takeIf { it.isNotBlank() }).joinToString(" · ")

/** One entry per client instance, with those of one person next to each other */
internal fun List<RemotePeer>.instances(): List<RemotePeer> =
    distinctBy { it.instanceId }.sortedWith(compareBy({ it.accountId ?: it.username }, { it.instanceId }))

/** As `#rrggbb`, which is what the server takes */
internal fun Color.toPeerColorString(): String = "#%06x".format(toArgb() and 0xFFFFFF)

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

/** A ghost is shown like a user who is still here */
internal fun RemoteGhost.asPeer() = RemotePeer(
    clientId = "ghost-$instanceId",
    username = username,
    displayName = displayName,
    color = color,
    selections = selections,
    accountId = accountId,
    viewport = viewport,
    instanceId = instanceId,
    deviceName = deviceName,
    instanceColor = instanceColor,
    avatar = avatar,
)
