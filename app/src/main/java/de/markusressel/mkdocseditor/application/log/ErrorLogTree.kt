package de.markusressel.mkdocseditor.application.log

import android.util.Log
import de.markusressel.mkdocsrestclient.sync.automerge.isAutomergePanic
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * Keeps errors and crashes in a small file that survives restarts, also in release builds, which log nothing else.
 *
 * Messages can hold text of the wiki, names or addresses, so an entry has only the kind of each error and where it
 * happened. The one message kept is that of a panic of Automerge, which comes from its native code. The file is
 * [FILE_NAME] in [directory], and the entries before it are in [OLD_FILE_NAME], each up to [maxBytes].
 */
internal class ErrorLogTree(
    private val directory: File,
    private val maxBytes: Long = MAX_BYTES,
    private val now: () -> Instant = Instant::now,
) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int) = priority >= Log.ERROR

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (t != null) write(t, fatal = false)
    }

    /** @param fatal whether it ended the app */
    @Synchronized
    fun write(throwable: Throwable, fatal: Boolean) {
        val entry = "${now()} ${if (fatal) "crash" else "error"}\n${describe(throwable)}\n"
        try {
            directory.mkdirs()
            val file = File(directory, FILE_NAME)
            if (file.exists() && file.length() + entry.length > maxBytes) {
                file.renameTo(File(directory, OLD_FILE_NAME).also { it.delete() })
            }
            file.appendText(entry.take(maxBytes.toInt()))
        } catch (e: IOException) {
            // nowhere left to tell
        } catch (e: SecurityException) {
        }
    }

    companion object {
        const val FILE_NAME = "errors.log"
        const val OLD_FILE_NAME = "errors.1.log"
        const val MAX_BYTES = 64L * 1024

        private const val MAX_FRAMES = 40
        private const val MAX_CAUSES = 5

        /** the kinds of [throwable] and its causes with their stacks, without messages except that of a panic */
        internal fun describe(throwable: Throwable): String = buildString {
            var current: Throwable? = throwable
            var depth = 0
            while (current != null && depth <= MAX_CAUSES) {
                if (depth > 0) append("Caused by: ")
                append(current.javaClass.name)
                if (current.isAutomergePanic()) append(": ").append(current.message)
                append('\n')
                current.stackTrace.take(MAX_FRAMES).forEach { append("\tat ").append(it).append('\n') }
                if (current.stackTrace.size > MAX_FRAMES) append("\t... ${current.stackTrace.size - MAX_FRAMES} more\n")
                current = current.cause?.takeUnless { it === current }
                depth++
            }
        }.trimEnd()
    }
}
