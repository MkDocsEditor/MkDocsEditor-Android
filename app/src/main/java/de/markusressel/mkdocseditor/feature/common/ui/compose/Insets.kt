package de.markusressel.mkdocseditor.feature.common.ui.compose

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The system bars and the display cutout: no control may sit under them. The Material defaults
 * only know the system bars, so in landscape a control could end up under a cutout at the side.
 * The keyboard is not part of it, because the screens make room for it themselves.
 */
internal val WindowInsets.Companion.barsAndCutout: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)

/**
 * Keeps the content of a bar clear of the system bars and the cutout at the sides, for bars that
 * are not Material app bars. Put it after the background, so the background still reaches the edges.
 */
@Composable
internal fun Modifier.horizontalBarsAndCutoutPadding(): Modifier =
    windowInsetsPadding(WindowInsets.barsAndCutout.only(WindowInsetsSides.Horizontal))
