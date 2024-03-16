package de.markusressel.mkdocseditor.storage

import android.content.Context
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes

/**
 * Manages all files within the internal app storage
 */
@Singleton
class AppStorageManager @Inject constructor(
    private val context: Context,
) {

    /**
     * Write data to a file
     */
    fun writeToFile(name: String, data: ByteArray): Path {
        return Path(context.filesDir.absolutePath, "resources", name).apply {
            parent?.createDirectories()
            writeBytes(data)
        }
    }

}