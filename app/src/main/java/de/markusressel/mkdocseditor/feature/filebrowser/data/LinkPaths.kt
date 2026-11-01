package de.markusressel.mkdocseditor.feature.filebrowser.data

import java.io.ByteArrayOutputStream

/**
 * The link path algorithm and the resolution of link destinations, as specified in
 * `docs/references.md` (sections 3 and 8) of MkDocsEditor-Backend. The backend uses the same
 * algorithm to rewrite references when items move, and `LinkPathsTest` runs its test vectors
 * (`docs/link-paths.json`).
 *
 * Paths are relative to the docs folder, `/` separated, without leading or trailing slash;
 * the root section has the path `""`.
 */
object LinkPaths {

    enum class TargetType { SECTION, DOCUMENT, RESOURCE }

    /**
     * @param path the path of the item
     * @param index for a section: the path of its index document (`index.md`, else
     * `README.md`), or empty
     */
    data class Target(val type: TargetType, val path: String, val index: String = "")

    /** ASCII characters that are percent-encoded besides control characters and space */
    private const val ENCODED_CHARS = "\"#%'()<>?[\\]^`{|}"

    private val urlScheme = "^[A-Za-z][A-Za-z0-9+.\\-]*:".toRegex()

    /**
     * The destination of a link from the document at [fromPath] to [target]
     *
     * @param anchor optional, without `#`
     */
    fun linkPath(fromPath: String, target: Target, anchor: String = ""): String {
        var targetPath = target.path
        var folder = false
        if (target.type == TargetType.SECTION) {
            if (target.index.isNotEmpty()) targetPath = target.index else folder = true
        }
        val encodedAnchor = if (anchor.isNotEmpty()) "#" + encode(anchor) else ""
        if (!folder && targetPath == fromPath) {
            return if (anchor.isNotEmpty()) encodedAnchor else encode(fromPath.substringAfterLast('/'))
        }
        return relativeLink(parentPath(fromPath), targetPath, folder) + encodedAnchor
    }

    private fun relativeLink(fromDir: String, targetPath: String, folder: Boolean): String {
        val from = split(fromDir)
        val to = split(targetPath)
        var common = 0
        while (common < from.size && common < to.size && from[common] == to[common]) common++
        val segments = List(from.size - common) { ".." } + to.drop(common).map { encode(it) }
        var result = segments.joinToString("/")
        if (folder) {
            result += "/"
            if (result == "/") result = "./"
        }
        if (segments.isNotEmpty() && segments[0].contains(':')) result = "./$result"
        return result
    }

    /** the folder of a path, `""` for a top-level item */
    fun parentPath(path: String): String = path.substringBeforeLast('/', "")

    private fun split(path: String): List<String> = if (path.isEmpty() || path == ".") emptyList() else path.split('/')

    /** percent-encodes a path segment or an anchor */
    fun encode(segment: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < segment.length) {
            val codePoint = segment.codePointAt(i)
            val size = Character.charCount(codePoint)
            if (codePoint < 0x80 && (codePoint <= 0x20 || codePoint == 0x7F || ENCODED_CHARS.indexOf(codePoint.toChar()) >= 0)) {
                result.append('%').append("%02X".format(codePoint))
            } else {
                result.append(segment, i, i + size)
            }
            i += size
        }
        return result.toString()
    }

    /** true for destinations with a URL scheme or starting with `//` */
    fun isExternal(destination: String): Boolean {
        val trimmed = destination.trim()
        return trimmed.startsWith("//") || urlScheme.containsMatchIn(trimmed)
    }

    /**
     * Resolves a destination written in the document at [fromPath] to a path below the docs
     * folder (steps 1 to 3 of the resolution)
     *
     * @return the path, or null for external destinations, same-document anchors and paths
     * that leave the docs folder
     */
    fun resolvePath(fromPath: String, destination: String): String? {
        val trimmed = destination.trim()
        if (trimmed.isEmpty() || isExternal(trimmed)) return null
        val target = trimmed.substringBefore('#').substringBefore('?')
        if (target.isEmpty()) return null
        val decoded = percentDecode(target)
        val full = if (decoded.startsWith("/")) decoded else parentPath(fromPath) + "/" + decoded
        val segments = mutableListOf<String>()
        for (segment in full.split('/')) {
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.lastIndex)
                else -> segments += segment
            }
        }
        return segments.joinToString("/")
    }

    /** decodes `%XX` escapes; invalid escapes stay as they are, `+` stays a plus sign */
    fun percentDecode(s: String): String {
        if (!s.contains('%')) return s
        val bytes = ByteArrayOutputStream()
        val utf8 = s.toByteArray(Charsets.UTF_8)
        var i = 0
        while (i < utf8.size) {
            val b = utf8[i]
            if (b == '%'.code.toByte() && i + 2 < utf8.size && isHex(utf8[i + 1]) && isHex(utf8[i + 2])) {
                bytes.write((hex(utf8[i + 1]) shl 4) or hex(utf8[i + 2]))
                i += 3
            } else {
                bytes.write(b.toInt())
                i++
            }
        }
        return bytes.toString(Charsets.UTF_8.name())
    }

    private fun isHex(b: Byte) = b.toInt().toChar().let { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    private fun hex(b: Byte): Int = Character.digit(b.toInt().toChar(), 16)
}
