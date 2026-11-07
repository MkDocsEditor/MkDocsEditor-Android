package de.markusressel.mkdocseditor.feature.common.ui

import de.markusressel.mkdocseditor.feature.common.data.TransferProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The upload or download that a screen shows with [de.markusressel.mkdocseditor.feature.common.ui.compose.TransferProgressBar],
 * and that the user can cancel. Several can run, the screen shows the one that reported last.
 *
 * @param scope runs the transfers, usually the scope of the ViewModel
 * @param update changes the transfer in the state of the screen. It is called on any thread, so it must be atomic,
 * like [kotlinx.coroutines.flow.MutableStateFlow.update].
 */
internal class ShownTransfer(
    private val scope: CoroutineScope,
    private val update: ((TransferProgress?) -> TransferProgress?) -> Unit,
) {
    @Volatile
    private var shownJob: Job? = null

    /**
     * Runs a transfer. Cancelling it only ends the transfer, not the caller.
     *
     * @param context where the transfer runs; a caller that blocks the main thread while it waits must move it away
     * @return the result, null when it was cancelled
     */
    suspend fun <T> track(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend (onProgress: (TransferProgress) -> Unit) -> T,
    ): T? {
        var shownId: Long? = null
        lateinit var job: Deferred<T>
        // lazy: the first progress comes before the network, and must find the job
        job = scope.async(context, start = CoroutineStart.LAZY) {
            block { progress ->
                shownId = progress.id
                shownJob = job
                update { progress }
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
