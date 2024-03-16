package de.markusressel.mkdocseditor.storage

import android.content.Context
import java.nio.file.Path
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeBytes

/**
 * Manages all files within the internal app storage
 */
@Singleton
class AppStorageManager @Inject constructor(
    private val context: Context,
) {

    /**
     * Get a file by name
     *
     * @param name the name of the file
     */
    fun getFile(name: String): Path {
        return Path(context.filesDir.absolutePath, "resources", name)
    }

    /**
     * Write data to a file
     *
     * @param name the name of the file
     * @param data the data to write
     * @param override whether to override an existing file
     * @return the path to the file
     * @throws FileAlreadyExistsException if the file already exists and override is false
     */
    fun writeToFile(name: String, data: ByteArray, override: Boolean = false): Path {
        val path = getFile(name)
        if (path.exists() && override.not()) throw FileAlreadyExistsException(path.toFile())
        return path.apply {
            parent?.createDirectories()
            writeBytes(data)
        }
    }

}