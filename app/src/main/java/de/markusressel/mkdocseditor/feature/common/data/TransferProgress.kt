package de.markusressel.mkdocseditor.feature.common.data

import java.util.concurrent.atomic.AtomicLong

/**
 * An upload or download that runs
 *
 * @param id tells transfers of the same file apart
 * @param doneBytes what has moved so far
 * @param totalBytes the whole size, 0 while unknown
 */
data class TransferProgress(
    val id: Long,
    val name: String,
    val upload: Boolean,
    val doneBytes: Long = 0,
    val totalBytes: Long = 0,
) {
    /** between 0 and 1, null while the size is unknown */
    val fraction: Float? get() = if (totalBytes > 0) (doneBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null

    companion object {
        private val ids = AtomicLong()

        fun start(name: String, upload: Boolean) = TransferProgress(ids.incrementAndGet(), name, upload)
    }
}

/**
 * Fuel reports every few kilobytes. This only passes on a step of a percent, so the screen doesn't redraw thousands
 * of times for a large file.
 */
internal fun percentSteps(onStep: (doneBytes: Long, totalBytes: Long) -> Unit): (Long, Long) -> Unit {
    var lastStep = -1L
    return { done, total ->
        val step = if (total > 0) done * 100 / total else done / UNKNOWN_SIZE_STEP_BYTES
        if (step != lastStep) {
            lastStep = step
            onStep(done, total)
        }
    }
}

/** without a known size, the bytes so far show in steps of this */
private const val UNKNOWN_SIZE_STEP_BYTES = 256 * 1024L
