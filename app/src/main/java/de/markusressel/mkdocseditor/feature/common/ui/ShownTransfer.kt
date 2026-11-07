package de.markusressel.mkdocseditor.feature.common.ui

import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** a transfer that ends before this never shows, so a quick one does not flash the bar */
internal const val SHOW_TRANSFER_AFTER_MS = 500L

/**
 * The upload or download that a screen shows with [de.markusressel.mkdocseditor.feature.common.ui.compose.TransferProgressBar],
 * and that the user can cancel. Only for transfers the user started, such as a tapped file; content that loads inline,
 * like the images of a page, shows as it arrives instead. Several can run, the screen shows the one that reported last.
 *
 * @param scope runs the transfers, usually the scope of the ViewModel
 * @param showAfterMs how long a transfer runs before it shows
 * @param update changes the transfer in the state of the screen. It is called on any thread, so it must be atomic,
 * like [kotlinx.coroutines.flow.MutableStateFlow.update].
 */
internal class ShownTransfer(
    private val scope: CoroutineScope,
    private val showAfterMs: Long = SHOW_TRANSFER_AFTER_MS,
    private val update: ((TransferProgress?) -> TransferProgress?) -> Unit,
) {
    @Volatile
    private var shownJob: Job? = null

    /**
     * Runs a transfer. Cancelling it only ends the transfer, not the caller.
     *
     * @return the result, null when it was cancelled
     */
    suspend fun <T> track(block: suspend (onProgress: (TransferProgress) -> Unit) -> T): T? {
        var shownId: Long? = null
        lateinit var job: Deferred<T>
        // lazy: the first progress comes before the network, and must find the job
        job = scope.async(start = CoroutineStart.LAZY) {
            // progress comes on a network thread, the delay ends on the thread of the scope
            val lock = Any()
            var latest: TransferProgress? = null
            var visible = false
            fun show(progress: TransferProgress) {
                shownId = progress.id
                shownJob = job
                update { progress }
            }

            val reveal = launch {
                delay(showAfterMs)
                synchronized(lock) {
                    visible = true
                    latest?.let(::show)
                }
            }
            try {
                block { progress ->
                    synchronized(lock) {
                        latest = progress
                        if (visible) show(progress)
                    }
                }
            } finally {
                reveal.cancel()
            }
        }
        return try {
            job.await()
        } catch (e: CancellationException) {
            // the caller goes on, unless it was cancelled itself
            currentCoroutineContext().ensureActive()
            null
        } finally {
            if (shownJob === job) shownJob = null
            update { old -> if (old?.id == shownId) null else old }
        }
    }

    /** stops the transfer that shows */
    fun cancel() {
        shownJob?.cancel()
    }
}
