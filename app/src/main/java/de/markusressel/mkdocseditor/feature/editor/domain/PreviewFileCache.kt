package de.markusressel.mkdocseditor.feature.editor.domain

import android.util.LruCache
import com.github.ajalt.timberkt.Timber
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceData
import de.markusressel.mkdocseditor.feature.filebrowser.domain.usecase.DownloadResourceUseCase
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The resources (images) shown by the preview. The preview renders again while the user types,
 * so files are kept in memory and downloaded only once per version. Downloads use the session
 * and also land in the offline file cache, which is used when the server is not reachable.
 */
@Singleton
class PreviewFileCache @Inject constructor(
    private val downloadResourceUseCase: DownloadResourceUseCase,
) {
    private val cache = object : LruCache<String, ByteArray>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ByteArray) = value.size
    }

    /**
     * @return the content of the resource, or null if it can't be loaded
     */
    suspend fun get(resource: ResourceData): ByteArray? {
        val key = "${resource.id}@${resource.modtime.time}"
        cache.get(key)?.let { return it }
        return try {
            val path = downloadResourceUseCase(resource.id, resource.name)
            Files.readAllBytes(path).also {
                if (it.size <= MAX_BYTES / 4) cache.put(key, it)
            }
        } catch (ex: Exception) {
            Timber.w(ex) { "Could not load resource ${resource.id} for the preview" }
            null
        }
    }

    /** forgets a resource, after it was replaced */
    fun evictAll() = cache.evictAll()

    private companion object {
        const val MAX_BYTES = 24 * 1024 * 1024
    }
}
