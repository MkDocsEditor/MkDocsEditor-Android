package de.markusressel.mkdocseditor.feature.auth.common.ui.compose

import androidx.compose.runtime.staticCompositionLocalOf
import cafe.adriel.voyager.navigator.Navigator

/**
 * Called when an auth screen that is the root of its own navigator (a link opened from
 * outside the app) is finished
 */
val LocalAuthFlowExit = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Pops the screen, or leaves the auth flow when it is the first screen
 */
fun Navigator.popOrExit(exit: () -> Unit) {
    if (canPop) pop() else exit()
}
