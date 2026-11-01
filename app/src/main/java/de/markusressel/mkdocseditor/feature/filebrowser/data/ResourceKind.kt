package de.markusressel.mkdocseditor.feature.filebrowser.data

/**
 * The kind of a resource, from its file extension (the same table as the backend and the web
 * client)
 */
enum class ResourceKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, ARCHIVE, OTHER;

    companion object {
        private val byExtension: Map<String, ResourceKind> = buildMap {
            listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "avif", "ico", "tif", "tiff").forEach { put(it, IMAGE) }
            listOf("mp4", "webm", "mov", "mkv", "avi", "m4v", "ogv").forEach { put(it, VIDEO) }
            listOf("mp3", "ogg", "oga", "wav", "flac", "m4a", "opus", "aac").forEach { put(it, AUDIO) }
            put("pdf", PDF)
            listOf("txt", "csv", "json", "xml", "yaml", "yml", "toml", "ini", "log", "css", "js", "html").forEach { put(it, TEXT) }
            listOf("zip", "gz", "tgz", "tar", "7z", "rar", "bz2", "xz").forEach { put(it, ARCHIVE) }
        }

        fun ofName(name: String): ResourceKind {
            val dot = name.lastIndexOf('.')
            val ext = if (dot > 0) name.substring(dot + 1).lowercase() else ""
            return byExtension[ext] ?: OTHER
        }
    }
}

fun ResourceData.resourceKind(): ResourceKind = ResourceKind.ofName(name)
