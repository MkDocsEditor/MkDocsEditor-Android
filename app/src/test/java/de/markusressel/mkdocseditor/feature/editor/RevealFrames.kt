package de.markusressel.mkdocseditor.feature.editor

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.snapshots.Snapshot
import de.markusressel.kodeeditor.library.compose.reveal.KodeEditorReveal
import de.markusressel.mkdocseditor.feature.editor.ui.EditorTextController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.coroutines.resume

/**
 * Runs the reveals of a controller like the editor screen does, with frames that come when a test asks for them.
 * Call [sync] after a change that may have started a reveal, so it notices the changes of the user from then on.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class RevealFrames(private val controller: EditorTextController) : AutoCloseable {

    private val scope = TestScope(UnconfinedTestDispatcher())
    private val running = mutableSetOf<KodeEditorReveal>()
    private val waiting = mutableListOf<(Long) -> Unit>()
    private var nanos = 0L

    private val clock = object : MonotonicFrameClock {
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
            suspendCancellableCoroutine { continuation -> waiting += { continuation.resume(onFrame(it)) } }
    }

    /** starts the reveals that are new, as the screen would in its next composition */
    fun sync() {
        Snapshot.sendApplyNotifications()
        controller.agentReveals.value.values.forEach { reveal ->
            if (running.add(reveal)) scope.launch(clock) { reveal.keepRevealing() }
        }
        Snapshot.sendApplyNotifications()
    }

    /** lets [ms] of frames of [FRAME_MS] pass */
    fun advance(ms: Long) {
        sync()
        var left = ms
        while (left > 0) {
            val step = minOf(left, FRAME_MS)
            nanos += step * 1_000_000
            val frame = waiting.toList()
            waiting.clear()
            frame.forEach { it(nanos) }
            Snapshot.sendApplyNotifications()
            controller.onRevealsMoved()
            left -= step
        }
    }

    override fun close() = scope.cancel()

    companion object {
        const val FRAME_MS = 16L
    }
}
