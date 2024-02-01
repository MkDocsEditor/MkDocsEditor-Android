package de.markusressel.mkdocseditor.extensions.common

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

suspend fun delayUntil(
    timeout: Duration = 1.seconds,
    checkInterval: Duration = 100.milliseconds,
    condition: suspend () -> Boolean
) {
    withTimeout(timeout) {
        while (condition().not()) {
            delay(checkInterval)
        }
    }
}